# updateapk
### android更新版本轮子
## 1.导入到工程
####    Step 1.Add it in your root build.gradle at the end of repositories:
  ```Java
  allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
  ```
####   Step 2. Add the dependency
  ```Java
  dependencies {
	        compile 'com.github.gypnick:updateapk:V1.1'
	}
  ```
##  2.使用
####  
     UpdateService.Builder.create(url)  //url为下载apk的地址
                .setVersionCode(2)  //这里设置服务器上的版本，通过这里来和本地versionCode版本对比 
                .setIsForce(true)  //是否强制更新，强制更新用户不更新就强制退出APP
                .build(MainActivity.this); 
  
# android7.0需要添加以下文件
###  1.在 AndroidManifest.xml中加入
        <provider
            android:name="android.support.v4.content.FileProvider"
            android:authorities="${applicationId}.fileProvider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
###  2.在 /src/main/res/xml/  加入file_paths.xml
      <?xml version="1.0" encoding="utf-8"?>
      <paths>
        <external-path path="Android/data/你的包名/" name="files_root" />
        <external-path path="." name="external_storage_root" />
      </paths>        
  
  
  
