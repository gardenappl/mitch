# Mitch

**Hosted on [itch.io](https://gardenapple.itch.io/mitch) and [SourceHut](https://sr.ht/~gardenapple/mitch/).** <sup>[why SourceHut?](#why-sourcehut)</sup>

(mirrored on [GitHub](https://github.com/gardenappl/mitch) and [GitLab](https://gitlab.com/gardenappl/mitch))

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
git clone https://git.sr.ht/~gardenapple/mitch.git
cd mitch
./gradlew build
```

**Windows:**

```
git clone https://git.sr.ht/~gardenapple/mitch.git
cd mitch
gradlew.bat build
```

Instead of using the `build` task (which builds every variant of the app and can take a while), consider building only one flavor: replace `build` with `assembleFdroidRelease` or `assembleItchioRelease`.

## Contributing

If you're on `git.sr.ht` right now, click [here to go to the main project page on sr.ht](https://sr.ht/~gardenapple/mitch/).

For sending code:

* Send a patch to the [development mailing list](https://lists.sr.ht/~gardenapple/mitch-devel).
* ...or send a pull request on GitHub, it's fine, I'm not a huge fan of mailing lists either.

For reporting bugs and issues:

* Open a ticket on the [issue tracker](https://todo.sr.ht/~gardenapple/mitch).
* ...or start a thread on [the itch.io forum](https://gardenapple.itch.io/mitch/community).

### Translating

Translations are provided by the generous people at [Weblate](https://weblate.org).

[If you'd like to contribute to Mitch, consider helping out with translations!](https://hosted.weblate.org/projects/mitch)

<a href="https://hosted.weblate.org/engage/mitch/">
<img src="https://hosted.weblate.org/widgets/mitch/-/multi-red.svg" alt="Стан перекладу" />
</a>

### Why SourceHut?

I still have doubts about the minimalist/brutalist workflows on SourceHut. The main reason why I chose it for Mitch was because I wanted an easy way to collect bug reports outside of Google Analytics. I didn't want to self-host an ACRA backend, so I set up a [SourceHut mailing list](https://lists.sr.ht/~gardenapple/mitch-bug-reports) as a quick and dirty solution for gathering bug reports. This has the added benefit of making user consent very explicit: you must agree to send the crash report as an email attachment from your mail app.

In practice, this does not work well, because 99% of emails that are sent by phone users are sent as HTML, which is [A Bad Thing](https://useplaintext.email/) and therefore rejected by SourceHut. So the mailing list is mostly empty and only my personal inbox gets filled. Mitch tells people to use plain text if they can, but perhaps I can encourage this further by making a mailing app that does this in a nice way by default.
