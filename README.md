# Bhilwara Wildlife Explorer — Android WebView app

This project wraps the provided HTML in a fullscreen Android WebView app, set to landscape orientation.

## Build an APK using GitHub Actions (no local Android Studio required)

1. Sign in to GitHub and create a **new repository** (a private repository is fine).
2. Extract this ZIP on your computer.
3. Upload **all files and folders inside** the extracted project to the repository's root. Make sure `.github/workflows/build-apk.yml` is included. (If GitHub's web upload hides dot-folders, use GitHub Desktop or the web editor to add the workflow.)
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab. The workflow named **Build Android APK** should start automatically after the push. You can also select it and click **Run workflow**.
6. Wait for the run to complete successfully.
7. Open that workflow run and scroll to **Artifacts**. Download `BhilwaraWildlifeExplorer-APK`.
8. Extract the artifact ZIP. It contains `app-debug.apk`.
9. Copy `app-debug.apk` to your Android panel and install it. If Android blocks installation, enable **Install unknown apps** for the app used to open the APK.

The APK is a debug-signed installable APK suitable for testing/deployment on your own panel. A production/release APK should be signed with your own release key.

## Project contents
- `app/src/main/assets/index.html` — your original HTML
- `app/src/main/java/in/gov/bhilwara/wildlife/MainActivity.java` — WebView app
- `.github/workflows/build-apk.yml` — cloud build workflow

## Media note
The HTML references online Unsplash images and sample MP4 video URLs. Internet access is required for those resources. Replace the sample video links in `index.html` with your final media before building if needed.
