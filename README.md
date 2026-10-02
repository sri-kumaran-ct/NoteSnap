<div align="center">

<img width="1200" height="475" alt="Android Application" src="https://developer.android.com/static/studio/images/studio-icon-preview.png" />

</div>

# Run and Deploy Your Android App

This repository contains everything you need to build, run, and deploy the Android application locally using Android Studio.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open **Android Studio**.

2. Select **Open** and choose the directory containing this project.

3. Allow Android Studio to sync the project and download any required dependencies.

4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` to your Gemini API key (see `.env.example` for an example).

5. Open the app module's `build.gradle.kts` file and make sure the project is configured to use the appropriate debug signing configuration for local development.

6. Sync the project by selecting **File → Sync Project with Gradle Files**.

7. Connect an Android device or start an Android Emulator.

8. Select the desired device from the device selector in Android Studio and click **Run ▶**.

9. Android Studio will build and install the application on the selected device.

## Building the APK

To generate an APK for testing:

1. Open the **Build** menu in Android Studio.
2. Select **Build Bundle(s) / APK(s)**.
3. Choose **Build APK(s)**.
4. Once the build is complete, Android Studio will provide the location of the generated APK.

## API Key Configuration

The application uses the Gemini API for AI-powered features. Make sure your Gemini API key is configured correctly before running the application.

**Never commit your actual API key to GitHub.** Add `.env` to your `.gitignore` file and use `.env.example` to document the required environment variable.

## Running on a Physical Device

To run the application on a physical Android device:

1. Enable **Developer Options** on your device.
2. Enable **USB Debugging**.
3. Connect the device to your computer using a USB cable.
4. Allow the computer to access the device when prompted.
5. Select the device in Android Studio.
6. Click **Run ▶** to install and launch the application.
