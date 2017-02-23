
## Usage

```java

//the bspatch of the whole zip
compile 'me.ele:bspatch:1.0.6'

//do anything first, you must call this
BsPatch.init(context)

//simple as this, use the code in non-main thread
boolean success = BsPatch.workSync(oldApkPath, newApkPath, patchApkPath);
BsPatch.workAsync(oldApkPath, newApkPath, patchApkPath, listener);


//the bspatch for each file in zip file
compile 'me.ele:zippatch:1.0.6'

ZipPatch.patchSync(oldApk, newApk, patchApk)
ZipPatch.patchAsync(oldApk, newApk, patchApk, listener)
```

# Compatibility
- support from FROYO `2.3`

## Tools for diff & patch 

calculate two apks's diff, you need **bsdiff**, it locates in **/tools/bsdiff**
this cli may be used in server side

```shell
 bsdiff oldfile newfile patchfile
```

if you want to use **zippatch**, generate patch file follow the [zippatch_server](https://github.com/eleme/bspatch/tree/master/zippatch_server)

### notice

sync with jcenter maybe need some time, if library can't be found, please use my personal maven center
 
 ```groovy
  repositories {
         jcenter()
         maven {
             url "https://dl.bintray.com/jackcho/maven"
         }
     }
 ```

license
====

	  Copyright 2016 ELEME Inc.

	  Licensed under the Apache License, Version 2.0 (the "License");
	  you may not use this file except in compliance with the License.
	  You may obtain a copy of the License at

	     http://www.apache.org/licenses/LICENSE-2.0

	  Unless required by applicable law or agreed to in writing, software
	  distributed under the License is distributed on an "AS IS" BASIS,
	  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	  See the License for the specific language governing permissions and
	  limitations under the License.

