/*
 * File: OtpMessageTemplates.kt
 * Module: feature.auth.delivery
 *
 * Centralised message-body builder. Every provider (SMS, WhatsApp, email,
 * voice) renders the SAME template family so the user experience is
 * identical regardless of which channel won the dispatch race.
 *
 * SMS-route restrictions in India (DLT / TRAI)
 * --------------------------------------------
 * For SMS we must stick to a pre-approved DLT template ID (set in the
 * provider's env: MSG91_TEMPLATE_ID / FAST2SMS_DLT_TEMPLATE_ID). When the
 * provider supports variable substitution we send the *variables only*
 * and the gateway plugs them into the approved body server-side. The
 * `smsBody()` here is only used as a fallback when no DLT template ID
 * is configured (i.e. dev / non-India dispatch).
 *
 * WhatsApp & email
 * ----------------
 * No DLT constraints, so we send the full rendered body. WhatsApp Cloud
 * API technically also wants a template name pre-approved by Meta for
 * out-of-session messages — we surface that via WHATSAPP_TEMPLATE_NAME.
 *
 * Localisation
 * ------------
 * Hindi and Marathi strings included for the Indian user base. Falls
 * back to English when locale unsupported.
 */
package com.littlebridge.enrollplus.feature.auth.delivery

internal object OtpMessageTemplates {

    private const val BRAND = "VidyaPrayag"

    /** Short SMS body (≤ 160 chars per segment). Used only when no DLT template ID is set. */
    fun smsBody(code: String, ttlMinutes: Long, locale: String): String = when (locale.lowercase()) {
        "hi" -> "$code aapka $BRAND OTP hai. $ttlMinutes min me expire hoga. Kisi se share na karein."
        "mr" -> "$code ha tumcha $BRAND OTP aahe. $ttlMinutes min madhe sampel. Konashi share karu naka."
        else -> "$code is your $BRAND OTP. It expires in $ttlMinutes min. Do not share it with anyone."
    }

    /** WhatsApp body — slightly richer because there's no 160-char limit. */
    fun whatsappBody(code: String, ttlMinutes: Long, purpose: String, locale: String): String =
        when (locale.lowercase()) {
            "hi" -> "*$BRAND*\n\nAapka OTP: *$code*\nValid for $ttlMinutes min.\n\nKisi ke saath share na karein. Yeh $purpose ke liye hai."
            "mr" -> "*$BRAND*\n\nTumcha OTP: *$code*\nValid for $ttlMinutes min.\n\nKonashi share karu naka. He $purpose sathi aahe."
            else -> "*$BRAND*\n\nYour OTP is *$code*.\nIt is valid for $ttlMinutes minutes.\n\nDo not share this code with anyone. It is for $purpose."
        }

    /** Plain-text email body (also wrapped in HTML by SmtpEmailProvider). */
    fun emailBody(code: String, ttlMinutes: Long, purpose: String): String = """
        |Hello,
        |
        |Your $BRAND verification code is:
        |
        |    $code
        |
        |This code is valid for $ttlMinutes minutes and can only be used once.
        |If you did not request this, you can safely ignore this email.
        |
        |Purpose: $purpose
        |
        |— $BRAND
    """.trimMargin()

    /** Email subject line. */
    fun emailSubject(code: String): String = "$BRAND verification code: $code"

    /** HTML wrapper used by the SMTP provider. */
    fun emailHtmlBody(code: String, ttlMinutes: Long, purpose: String): String = """
        <!doctype html>
        <html><body style="font-family: -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif; padding:24px; background:#f6f7fb; color:#1f2937;">
          <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:12px;padding:24px;border:1px solid #e5e7eb;">
            <div style="font-weight:700;font-size:18px;color:#4f46e5;margin-bottom:8px;">$BRAND</div>
            <div style="font-size:15px;line-height:1.55;color:#374151;">Hello, your verification code is:</div>
            <div style="font-size:34px;letter-spacing:8px;font-weight:700;margin:20px 0;color:#111827;">$code</div>
            <div style="font-size:14px;line-height:1.55;color:#4b5563;">
              This code is valid for <b>$ttlMinutes minutes</b> and can only be used once. If you did not request this, you can safely ignore this email.
            </div>
            <div style="font-size:12px;color:#9ca3af;margin-top:24px;">Purpose: $purpose</div>
          </div>
          <div style="text-align:center;color:#9ca3af;font-size:12px;margin-top:16px;">— $BRAND security team</div>
        </body></html>
    """.trimIndent()
}
