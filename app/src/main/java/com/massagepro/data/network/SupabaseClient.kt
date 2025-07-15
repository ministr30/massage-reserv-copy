package com.massagepro.data.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime




object SupabaseClient {
    private const val SUPABASE_URL = "https://pemkivbnriqazkscrgbt.supabase.co"
    private const val SUPABASE_ANON_KEY = "код надо вставить"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
