/*-
 * Copyright 2003-2005 Colin Percival
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */


#include <bzlib.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/types.h>    // android
#include "bspatch.h"
#include <android/log.h>	// To be able to print to logcat

static off_t offtin(u_char *buf) {
    off_t y;

    y = buf[7] & 0x7F;
    y = y * 256; y += buf[6];
    y = y * 256; y += buf[5];
    y = y * 256; y += buf[4];
    y = y * 256; y += buf[3];
    y = y * 256; y += buf[2];
    y = y * 256; y += buf[1];
    y = y * 256; y += buf[0];

    if (buf[7] & 0x80) {
        y = -y;
    }

    return y;
}

int bspatch(int argc, const char **argv) {
    FILE *f = NULL, *cpf = NULL, *dpf = NULL, *epf = NULL;
    BZFILE *cpfbz2 = NULL, *dpfbz2 = NULL, *epfbz2 = NULL;
    int cbz2err, dbz2err, ebz2err;
    int fd;
    ssize_t oldsize, newsize;
    ssize_t bzctrllen, bzdatalen;
    u_char header[32], buf[8];
    u_char *old = NULL, *new = NULL;
    off_t oldpos, newpos;
    off_t ctrl[3];
    off_t lenread;
    off_t i;
    int ret = -1;

    if (argc != 4) {
        ALOGE("usage: %s oldfile newfile patchfile\n", argv[0]);
        return -1;
    }

    ALOGI("bspatch: old=%s  new=%s  patch=%s", argv[1], argv[2], argv[3]);

    /* Open patch file */
    f = fopen(argv[3], "r");
    if (f == NULL) {
        ALOGE("Failed to open '%s': %s", argv[3], strerror(errno));
        return -1;
    }

    /*
       File format:
       Offset   Len     Description
       0        8       "BSDIFF40"
       8        8       X
       16       8       Y
       24       8       sizeof(newfile)
       32       X       bzip2(control block)
       32+X     Y       bzip2(diff block)
       32+X+Y   ???     bzip2(extra block)

       with control block a set of triples (x,y,z) meaning "add x bytes
       from oldfile to x bytes from the diff block; copy y bytes from the
       extra block; seek forwards in oldfile by z bytes".
    */

    /* Read header */
    if (fread(header, 1, 32, f) < 32) {
        if (feof(f)) {
            ALOGE("Incorrect header (too small)");
        }
        ALOGE("Failed to read the header from '%s': %s", argv[3], strerror(errno));

        if (fclose(f)) {
            ALOGE("Failed to close '%s'i: %s", argv[3], strerror(errno));
        }
        return -1;
    }

    /* Close patch file and re-open it via libbzip2 at the right places */
    if (fclose(f)) {
        ALOGE("Failed to close '%s': %s", argv[3], strerror(errno));
        return -1;
    }

    /* Check for appropriate magic */
    if (memcmp(header, "BSDIFF40", 8) != 0) {
        ALOGE("Failed to verify the magic");
        return -1;
    }

    /* Read lengths from header */
    bzctrllen = offtin(header+8);
    bzdatalen = offtin(header+16);
    newsize = offtin(header+24);
    if ((bzctrllen < 0) || (bzdatalen < 0) || (newsize < 0)) {
        ALOGE("Incorrect header data");
        return -1;
    }

    if ((cpf = fopen(argv[3], "r")) == NULL) {
        ALOGE("[Control block] Failed to open '%s': %s", argv[3], strerror(errno));
        return -1;
    }

    if (fseeko(cpf, 32, SEEK_SET)) {
        ALOGE("[Control block] fseeko() failed on '%s': %s", argv[3], strerror(errno));
        goto end;
    }

    if ((cpfbz2 = BZ2_bzReadOpen(&cbz2err, cpf, 0, 0, NULL, 0)) == NULL) {
        ALOGE("[Control block] BZ2_bzReadOpen() failed, bz2err = %d", cbz2err);
        goto end;
    }

    if ((dpf = fopen(argv[3], "r")) == NULL) {
        ALOGE("[Diff block] Failed to open '%s': %s", argv[3], strerror(errno));
        goto end;
    }

    if (fseeko(dpf, 32 + bzctrllen, SEEK_SET)) {
        ALOGE("[Diff block] fseeko() failed on '%s': %s", argv[3], strerror(errno));
        goto end;
    }

    if ((dpfbz2 = BZ2_bzReadOpen(&dbz2err, dpf, 0, 0, NULL, 0)) == NULL) {
        ALOGE("[Diff block] BZ2_bzReadOpen() failed, bz2err = %d", dbz2err);
        goto end;
    }

    if ((epf = fopen(argv[3], "r")) == NULL) {
        ALOGE("[Extra block] Failed to open '%s': %s", argv[3], strerror(errno));
        goto end;
    }

    if (fseeko(epf, 32 + bzctrllen + bzdatalen, SEEK_SET)) {
        ALOGE("[Extra block] fseeko() failed on '%s': %s", argv[3], strerror(errno));
        goto end;
    }

    if ((epfbz2 = BZ2_bzReadOpen(&ebz2err, epf, 0, 0, NULL, 0)) == NULL) {
        ALOGE("[Extra block] BZ2_bzReadOpen() failed, bz2err = %d", ebz2err);
        goto end;
    }

    ALOGI("Opening %s", argv[1]);

    if (((fd = open(argv[1], O_RDONLY, 0)) < 0) ||
            ((oldsize = lseek(fd, 0, SEEK_END)) == -1) ||
            ((old = malloc(oldsize+1)) == NULL) ||
            (lseek(fd, 0, SEEK_SET) != 0) ||
            (read(fd, old, oldsize) != oldsize) ||
            (close(fd) == -1)) {
        ALOGE("Failed to read %s: %s", argv[1], strerror(errno));
        goto end;
    }

    if ((new = malloc(newsize+1)) == NULL) {
        ALOGE("Failed to allocate size for the new file");
        goto end;
    }

    oldpos = 0;
    newpos = 0;

    while (newpos < newsize) {
        /* Read control data */
        for (i = 0; i <= 2; i++) {
            lenread = BZ2_bzRead(&cbz2err, cpfbz2, buf, 8);
            if ((lenread < 8) || ((cbz2err != BZ_OK) &&
                        (cbz2err != BZ_STREAM_END))) {
                ALOGE("Failed to read control data");
                goto end;
            }
            ctrl[i] = offtin(buf);
        }

        /* Sanity-check */
        if (newpos + ctrl[0] > newsize) {
            ALOGE("[Control block] Size don't match");
            goto end;
        }

        /* Read diff string */
        lenread = BZ2_bzRead(&dbz2err, dpfbz2, new + newpos, ctrl[0]);
        if ((lenread < ctrl[0]) ||
                ((dbz2err != BZ_OK) && (dbz2err != BZ_STREAM_END))) {
            ALOGE("Failed to read diff data");
            goto end;
        }

        /* Add old data to diff string */
        for (i = 0; i < ctrl[0]; i++) {
            if ((oldpos + i >= 0) && (oldpos + i < oldsize)) {
                new[newpos+i] += old[oldpos+i];
            }
        }

        /* Adjust pointers */
        newpos += ctrl[0];
        oldpos += ctrl[0];

        /* Sanity-check */
        if (newpos + ctrl[1] > newsize) {
            ALOGE("[Diff block] Size don't match");
            goto end;
        }

        /* Read extra string */
        lenread = BZ2_bzRead(&ebz2err, epfbz2, new + newpos, ctrl[1]);
        if ((lenread < ctrl[1]) ||
                ((ebz2err != BZ_OK) && (ebz2err != BZ_STREAM_END))) {
            ALOGE("Failed to read extra data");
            goto end;
        }

        /* Adjust pointers */
        newpos += ctrl[1];
        oldpos += ctrl[2];
    }

    ALOGI("Writing the new file '%s'", argv[2]);

    /* Write the new file */
    if (((fd = open(argv[2], O_CREAT|O_TRUNC | O_WRONLY, 0666)) < 0) ||
            (write(fd, new, newsize) != newsize) || (close(fd) == -1)) {
        ALOGE("Failed to write to '%s': %s", argv[2], strerror(errno));
        goto end;
    }

    ALOGI("Done writing the new file");

    ret = 0;

end:
    /* Clean up the bzip2 reads */
    ALOGI("Closing bzip2 reads");
    if (cpfbz2 != NULL) {
        BZ2_bzReadClose(&cbz2err, cpfbz2);
    }
    if (dpfbz2 != NULL) {
        BZ2_bzReadClose(&dbz2err, dpfbz2);
    }
    if (epfbz2 != NULL) {
        BZ2_bzReadClose(&ebz2err, epfbz2);
    }

    if (cpf != NULL) {
        if (fclose(cpf)) {
            ALOGE("Failed to close cpf '%s': %s", argv[3], strerror(errno));
        }
    }
    if (dpf != NULL) {
        if (fclose(dpf)) {
            ALOGE("Failed to close dpf '%s': %s", argv[3], strerror(errno));
        }
    }
    if (epf != NULL) {
        if (fclose(epf)) {
            ALOGE("Failed to close epf '%s': %s", argv[3], strerror(errno));
        }
    }

    if (new != NULL) {
        free(new);
    }
    if (old != NULL) {
        free(old);
    }

    if (ret == 0) {
        ALOGI("bspatch completed successfully!");
    } else {
        ALOGI("bspatch failed !!");
    }

    return ret;
}

void jniThrowException(JNIEnv* env, const char* className, const char* msg) {
    jclass exceptionClass = (*env)->FindClass(env, className);
    if (exceptionClass == NULL) { /* Unable to find the new exception class, give up. */
        ALOGE("jniThrowException failed: exception class '%s' not found", className);
        return;
    }

    (*env)->ThrowNew(env, exceptionClass, msg);
}

