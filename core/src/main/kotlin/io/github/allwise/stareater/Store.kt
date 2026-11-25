package io.github.allwise.stareater

/**
 * The one thing for sale: the full game, unlocked once. Google Play on Android; on desktop there is
 * no store and the whole game is simply there.
 *
 * Calls back on whatever thread the platform likes; [StarEaterGame] moves the answers onto the
 * game's own thread before anything is touched.
 */
interface Store {
    /** False where there is nothing to buy, and so no demo either. */
    val sells: Boolean

    /** Starts talking to the store. Ownership is checked straight away, which also restores a purchase after a reinstall. */
    fun connect(listener: StoreListener) {}

    /** Opens the store's own purchase sheet. */
    fun buy() {}

    /** Asks the store again what this account owns. */
    fun restore() {}

    object None : Store {
        override val sells = false
    }
}

interface StoreListener {
    /** The store's word on whether this account owns the full game. Only sent on a clean answer. */
    fun owned(full: Boolean)

    /** The price, already formatted in the buyer's own currency. */
    fun price(text: String)

    /** Something worth telling the player: a purchase waiting on payment, a store that cannot be reached. */
    fun note(text: String)
}

/**
 * A pretend store for trying the demo and the unlock on desktop (`trial=1`): it sells, and a purchase
 * goes through a moment after BUY is pressed.
 */
class PretendStore : Store {
    override val sells = true
    private var listener: StoreListener? = null

    override fun connect(listener: StoreListener) {
        this.listener = listener
        listener.price("NOK 49.00")
        listener.owned(false)
    }

    override fun buy() {
        Thread {
            Thread.sleep(1200)
            listener?.owned(true)
        }.apply { isDaemon = true }.start()
    }

    override fun restore() {
        listener?.note("NOTHING TO RESTORE ON THIS ACCOUNT")
    }
}
