@echo off
set JAVA_HOME=D:\Android\Android Studio\jbr
cd /d d:\video_player
call gradlew.bat assembleRelease --console=plain > build_log_tmp.txt 2>&1
echo EXITCODE=%ERRORLEVEL%
