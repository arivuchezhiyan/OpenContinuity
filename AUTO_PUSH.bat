@echo off
REM AUTOMATED PUSH SOLUTION
REM This will remove large files and push everything

cd /d "C:\Users\arivu\OneDrive\Desktop\OpenContinuity"

echo Checking current status...
git status

echo.
echo Removing large files from Git...
git rm -r --cached windows/release/

echo.
echo Creating commit...
git commit -m "Remove build artifacts from Git - Build executables should not be version controlled

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"

echo.
echo Pushing to GitHub...
git push origin main

echo.
echo Done! Check GitHub to verify.
pause
