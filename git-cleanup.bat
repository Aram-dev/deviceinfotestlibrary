@echo off
echo Starting cleanup...

REM Remove commonly tracked build/tool folders
git rm -r --cached .gradle
git rm -r --cached build
git rm -r --cached caches
git rm -r --cached daemon
git rm -r --cached native
git rm -r --cached .idea
git rm -r --cached wrapper/dists

REM Confirm cleanup
echo Committing cleanup...
git commit -m "Cleanup: remove unnecessary files from Git tracking"
git push

echo Cleanup completed.
pause
