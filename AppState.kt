object AppState {
    var currentUserProfile: UserProfile? = null
    var onProfileUpdated: (() -> Unit)? = null

    fun updateProfile(profile: UserProfile) {
        currentUserProfile = profile
        onProfileUpdated?.invoke()
    }
}