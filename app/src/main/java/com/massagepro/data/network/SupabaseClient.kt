package com.massagepro.data.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime




object SupabaseClient {
    private const val SUPABASE_URL = "https://pemkivbnriqazkscrgbt.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBlbWtpdmJucmlxYXprc2NyZ2J0Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxNzc4NTEsImV4cCI6MjA2Nzc1Mzg1MX0.HlkO-Cz80iefAFXYSk-5iU8Fz4kRCVNzB1m5LpjU1lc"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
