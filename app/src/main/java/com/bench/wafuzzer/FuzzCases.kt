package com.bench.wafuzzer

import android.content.Intent
import android.net.Uri

data class FuzzCase(
    val label: String,
    val build: () -> Intent
)

object FuzzCases {

    private const val WA = "com.whatsapp"

    val all: List<FuzzCase> = listOf(

        // 1. View intent dengan URI kosong / aneh
        FuzzCase("view-empty") {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(WA)
                data = Uri.parse("whatsapp://")
            }
        },

        // 2. deeplink send dengan nomor sangat panjang
        FuzzCase("send-overflow") {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(WA)
                data = Uri.parse("https://wa.me/" + "9".repeat(4096))
            }
        },

        // 3. deeplink dengan persen-encoding rusak
        FuzzCase("bad-percent") {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(WA)
                data = Uri.parse("https://wa.me/6281234567890?text=%ZZ%ZZ%ZZ")
            }
        },

        // 4. share teks berisi null byte
        FuzzCase("share-null") {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(WA)
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "a\u0000b\u0000c")
            }
        },

        // 5. share tipe MIME invalid
        FuzzCase("share-mime-garbage") {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(WA)
                type = "text/\u0000\u0000"
                putExtra(Intent.EXTRA_TEXT, "x")
            }
        },

        // 6. share file URI yang tidak ada
        FuzzCase("share-missing-file") {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(WA)
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///does/not/exist.png"))
            }
        },

        // 7. vCard kosong via SEND
        FuzzCase("share-empty-vcard") {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(WA)
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_TEXT, "BEGIN:VCARD\nEND:VCARD")
            }
        },

        // 8. deeplink call dengan skema salah
        FuzzCase("bad-scheme") {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(WA)
                data = Uri.parse("whatsapp-xyz://call?phone=123")
            }
        },

        // 9. intent dengan extra bundle berisi key berulang
        FuzzCase("dup-extras") {
            Intent(Intent.ACTION_SEND).apply {
                setPackage(WA)
                type = "text/plain"
                // Android tidak allow duplicate key, tapi kita coba via parcel trick
                putExtra("jid", "a@s.whatsapp.net")
                putExtra("jid", "\u0000\u0000\u0000")
            }
        },

        // 10. deeplink grup dengan ID kosong
        FuzzCase("empty-group") {
            Intent(Intent.ACTION_VIEW).apply {
                setPackage(WA)
                data = Uri.parse("https://chat.whatsapp.com/")
            }
        }
    )
}
