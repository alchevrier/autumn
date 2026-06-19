package dev.autumn.bff.resolver

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

object CountryResolver {

    private const val FALLBACK_COUNTRY = "US"

    /**
     * Extracts the user's country code from the request headers to provide localized configurations.
     * Common edge networks like Cloudflare attach CF-IPCountry.
     */
    fun resolve(call: ApplicationCall): String {
        return call.request.header("CF-IPCountry") 
            ?: call.request.header("X-Country-Code")
            ?: FALLBACK_COUNTRY
    }
}
