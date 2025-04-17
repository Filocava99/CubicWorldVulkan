@echo off
echo Starting CubicWorld with increased memory settings...
java -Xss8m -Xmx2g -Dorg.lwjgl.util.Debug=true -jar build/libs/CubicWorld-1.0-SNAPSHOT.jar
echo Application exited.
pause
