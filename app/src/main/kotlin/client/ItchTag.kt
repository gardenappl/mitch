package garden.appl.mitch.client

data class ItchTag(
    /**
     * Localized name for this tag
     */
    val name: String,
    val url: String,
    val tag: String,
    /**
     * Set to true for tags specific for a [Classification].
     */
    val primary: Boolean
) {
    enum class Classification(val slug: String) {
        GAME("game"),
        ASSETS("assets"),
        GAME_MOD("game_mod"),
        PHYSICAL_GAME("physical_game"),
        SOUNDTRACK("soundtrack"),
        OTHER("other"),
        TOOL("tool"),
        COMIC("comic"),
        BOOK("book")
    }
}
