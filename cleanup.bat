@echo off
REM Git Cleanup Script - Remove Build Artifacts
cd /d "C:\Users\arivu\OneDrive\Desktop\OpenContinuity"

echo.
echo ===============================================
echo Step 1: Check current git status
echo ===============================================
git status

echo.
echo ===============================================
echo Step 2: Remove windows/release from git tracking
echo ===============================================
git rm -r --cached windows/release/

echo.
echo ===============================================
echo Step 3: Verify what will be removed
echo ===============================================
git status

echo.
echo ===============================================
echo Step 4: Create cleanup commit
echo ===============================================
git commit -m "Remove build artifacts from Git

Build executables should not be in version control:
- Removed windows/release/ directory (74MB + 168MB .exe files)
- Build artifacts bloat repository and cause push failures
- Users can download builds from GitHub Releases instead

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"

echo.
echo ===============================================
echo Step 5: Verify commit was created
echo ===============================================
git log --oneline -n 3

echo.
echo ===============================================
echo Step 6: Push to GitHub
echo ===============================================
git push origin main

echo.
echo ===============================================
echo SUCCESS! Push completed
echo ===============================================
echo.
echo Next steps:
echo 1. Go to GitHub: https://github.com/arivuchezhiyan/OpenContinuity/releases
echo 2. Create a new release (tag: v1.0.0)
echo 3. Upload your .exe files from windows/release/
echo.
pause
