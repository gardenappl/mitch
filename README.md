# Mitch

### The main repository of this project is hosted on GitLab: https://gitlab.com/gardenappl/mitch

**Mitch** is an unofficial mobile client for [itch.io](https://itch.io), the indie game storefront. The app allows you to install Android games from the store and keep them updated. Thus, this can act as an alternative to the Google Play Store for indie game developers and enthusiasts.

  * Download games from itch.io and keep them updated
  * Support for APK downloads as well as HTML5 games
  * itch.io account is not required
  * Blocks trackers and analytics by default (F-Droid version only)
  * Beautiful Material UI adapts to custom color schemes set by game developers
  * Full functionality of itch.io's mobile website

The app is still in development, some features are planned but not yet implemented.

## Installing

**Mitch is available on F-Droid**:
<a href="https://f-droid.org/packages/ua.gardenapple.itchupdater"><br> <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80px"></a>

There are other ways to get this app:

* **Buy the APK from [itch.io](https://gardenapple.itch.io/mitch)**
* Compile from source

## Compiling from source

This is a standard Android Studio project, which relies on Gradle.

**Unix-y systems:**

```
git clone https://gitlab.com/gardenappl/mitch.git
cd mitch
./gradlew build
```

**Windows:**

```
git clone https://gitlab.com/gardenappl/mitch.git
cd mitch
gradlew.bat build
```

Instead of using the `build` task (which builds every variant of the app and can take a while), consider building only one flavor: replace `build` with `assembleFdroidRelease` or `assembleItchioRelease`.
