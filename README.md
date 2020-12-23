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

**If you wish to support this app, consider [buying it on itch.io](https://gardenapple.itch.io/mitch)**

## Installing

There are a few ways to get this app:

* Get the APK from [GitLab releases](https://gitlab.com/gardenappl/mitch/-/releases)
* Get the APK from [itch.io](https://gardenapple.itch.io/mitch)
* Compiling from source

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
