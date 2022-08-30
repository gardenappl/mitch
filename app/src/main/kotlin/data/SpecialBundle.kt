package garden.appl.mitch.data

enum class SpecialBundle(val gameIDs: Array<Int>, val slug: String, val bundleId: Int, val url: String) {
    RacialJustice(BundleJusticeGameIDs, "racial", 520,  "https://itch.io/b/520/bundle-for-racial-justice-and-equality"),
    Palestine(BundlePalestineGameIDs, "palestine", 902, "https://itch.io/b/902/indie-bundle-for-palestinian-aid"),
    TransTexas(BundleTransTexasGameIDs, "trans_texas",  1308, "https://itch.io/b/1308/ttrpgs-for-trans-rights-in-texas"),
    Ukraine(BundleUkraineGameIDs, "ukraine", 1316,  "https://itch.io/b/1316/bundle-for-ukraine")
}

fun SpecialBundle.containsGame(gameId: Int): Boolean {
    return this.gameIDs.binarySearch(gameId) >= 0
}