package com.example.data.local

/**
 * The app stores everything on the device for a single user, so there is only one owner id. It is
 * kept on every stored row (and used to name per-user files) so existing queries and models keep
 * their shape.
 */
const val LOCAL_USER_ID = "local_user"
