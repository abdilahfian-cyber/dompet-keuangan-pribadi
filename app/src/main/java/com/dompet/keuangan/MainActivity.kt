package com.dompet.keuangan

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.ExifInterface
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : android.app.Activity() {
    private class Tx(val out: Boolean, val amount: Long, val cat: String, val note: String, val time: Long)
    private class Mo(val label: String, val inc: Long, val out: Long)

    private lateinit var container: FrameLayout
    private var current: View? = null
    private var onDashboard = true
    private val ease = DecelerateInterpolator(1.5f)
    private val prefs by lazy { getSharedPreferences("dompet", Context.MODE_PRIVATE) }
    private val txs = ArrayList<Tx>()
    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id","ID"))
    private val palette = intArrayOf(
        Color.rgb(37,99,235), Color.rgb(249,115,22), Color.rgb(22,163,74), Color.rgb(147,51,234),
        Color.rgb(219,39,119), Color.rgb(13,148,136), Color.rgb(202,138,4), Color.rgb(100,116,139)
    )
    private var judul = "Dompet"
    private var bgBitmap: Bitmap? = null
    private var saldo = 0L
    private var masuk = 0L
    private var keluar = 0L

    private fun money(v: Long): String =
        NumberFormat.getCurrencyInstance(Locale("id","ID")).format(v).replace(",00","")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun ico(v: TextView, res: Int) {
        v.setCompoundDrawablesRelativeWithIntrinsicBounds(res, 0, 0, 0)
        v.compoundDrawablePadding = dp(10)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(); recalc()
        container = FrameLayout(this)
        judul = prefs.getString("judul", "Dompet") ?: "Dompet"
        loadBackground()
        applyBackground()
        setContentView(container)
        showDashboard(animate = false)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!onDashboard) showDashboard(forward = false) else super.onBackPressed()
    }

    // ---------- Data: riwayat transaksi disimpan sebagai JSON di SharedPreferences ----------
    private fun load() {
        val s = prefs.getString("tx", null)
        if (s != null) {
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                txs.add(Tx(o.getBoolean("o"), o.getLong("a"), o.getString("c"), o.optString("n", ""), o.getLong("t")))
            }
        } else { // migrasi dari versi lama yang hanya menyimpan 3 total
            val now = System.currentTimeMillis()
            val m = prefs.getLong("masuk", 0); val k = prefs.getLong("keluar", 0)
            if (m > 0) txs.add(Tx(false, m, "Saldo awal", "Migrasi data lama", now))
            if (k > 0) txs.add(Tx(true, k, "Lainnya", "Migrasi data lama", now))
            persist()
        }
    }

    private fun persist() {
        val a = JSONArray()
        for (t in txs) a.put(JSONObject().put("o", t.out).put("a", t.amount).put("c", t.cat).put("n", t.note).put("t", t.time))
        prefs.edit().putString("tx", a.toString()).apply()
    }

    private fun recalc() {
        masuk = 0; keluar = 0
        for (t in txs) if (t.out) keluar += t.amount else masuk += t.amount
        saldo = masuk - keluar
    }

    // ---------- Transisi antar layar: geser + zoom + putar 3D halus (hanya properti transform/alpha) ----------
    private fun show(next: View, forward: Boolean, animate: Boolean) {
        while (container.childCount > 1) container.removeViewAt(0)
        val old = current
        current = next
        container.addView(next)
        if (!animate || old == null) {
            old?.let { container.removeView(it) }
            return
        }
        val dir = if (forward) 1f else -1f
        val shift = resources.displayMetrics.widthPixels * 0.3f * dir
        val cd = 12000f * resources.displayMetrics.density
        next.cameraDistance = cd; old.cameraDistance = cd
        next.translationX = shift; next.rotationY = -16f * dir
        next.scaleX = 0.92f; next.scaleY = 0.92f; next.alpha = 0f
        old.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        next.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        old.animate().translationX(-shift).rotationY(16f * dir).scaleX(0.92f).scaleY(0.92f).alpha(0f)
            .setDuration(320).setInterpolator(ease)
            .withEndAction { container.removeView(old); old.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
        next.animate().translationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(320).setInterpolator(ease)
            .withEndAction { next.setLayerType(View.LAYER_TYPE_NONE, null) }
            .start()
    }

    private fun tv(text:String, size:Float=16f, bold:Boolean=false): TextView {
        val t=TextView(this); t.text=text; t.textSize=size
        t.setTextColor(Color.rgb(20,30,45)); t.setPadding(0,8,0,8)
        if(bold) t.setTypeface(null,1)
        return t
    }

    private fun page(): LinearLayout {
        val root=LinearLayout(this); root.orientation=LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(24), dp(16), dp(24))
        return root
    }

    private fun scrolled(root: View): ScrollView {
        val s = ScrollView(this)
        s.isFillViewport = true
        s.addView(root)
        if (root is LinearLayout) {
            for (i in 0 until root.childCount) {
                val ch = root.getChildAt(i)
                ch.alpha = 0f; ch.translationY = dp(30).toFloat()
                ch.animate().alpha(1f).translationY(0f).setStartDelay(140L + minOf(i, 8) * 55L)
                    .setDuration(380).setInterpolator(ease).start()
            }
        }
        return s
    }

    private fun bg(c1: Int, c2: Int, r: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun solid(c: Int, r: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(c); g.cornerRadius = dp(r).toFloat()
        return g
    }

    private fun mlp(): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(8); p.bottomMargin = dp(8)
        return p
    }

    private fun styleBtn(b: Button) {
        b.transformationMethod = null
        b.textSize = 16f
        b.setTextColor(Color.rgb(30,41,59))
        b.setPadding(dp(18), dp(14), dp(18), dp(14))
        b.background = solid(Color.WHITE, 20)
        b.elevation = dp(4).toFloat()
    }

    private fun menuBtn(text: String, icon: Int, onClick: () -> Unit): Button {
        val b = Button(this); b.text = text
        styleBtn(b); ico(b, icon)
        b.setOnClickListener { onClick() }
        make3d(b)
        return b
    }

    // Kolom menu 3D: menekan = timbul, geser mendatar = berputar bebas 360 derajat lalu kembali ke posisi depan
    private fun make3d(v: View) {
        v.elevation = dp(6).toFloat()
        v.cameraDistance = 9000f * resources.displayMetrics.density
        var sx = 0f
        var drag = false
        var base = 0f
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; drag = false; base = view.rotationY
                    view.animate().scaleX(0.97f).scaleY(0.97f).setStartDelay(0).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx
                    if (!drag && abs(dx) > dp(14)) { drag = true; view.parent?.requestDisallowInterceptTouchEvent(true) }
                    if (drag) view.rotationY = base + dx * 0.6f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (drag) {
                        val target = Math.round(view.rotationY / 360f) * 360f
                        view.animate().scaleX(1f).scaleY(1f).rotationY(target).setStartDelay(0).setDuration(500)
                            .setInterpolator(OvershootInterpolator(1.1f)).withEndAction { view.rotationY = 0f }.start()
                    } else {
                        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(120)
                            .setInterpolator(DecelerateInterpolator()).start()
                        if (e.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                    }
                }
            }
            true
        }
    }

    // ---------- Dashboard ----------
    private fun showDashboard(forward: Boolean = false, animate: Boolean = true) {
        onDashboard = true
        val root = page()

        val head = LinearLayout(this); head.orientation = LinearLayout.VERTICAL
        head.setPadding(dp(20), dp(18), dp(20), dp(18))
        head.background = bg(Color.rgb(79,70,229), Color.rgb(14,165,233), 24)
        head.elevation = dp(6).toFloat()
        val h1 = tv(judul, 30f, true); h1.setTextColor(Color.WHITE)
        val h2 = tv("Keuangan pribadi • offline-first", 14f); h2.setTextColor(Color.rgb(224,231,255))
        head.addView(h1); head.addView(h2)
        root.addView(head, mlp())

        val balance = tv("Saldo\n${money(saldo)}", 25f, true)
        balance.setTextColor(Color.WHITE); balance.setPadding(dp(18), dp(20), dp(18), dp(20))
        balance.background = bg(Color.rgb(15,23,42), Color.rgb(67,56,202), 22)
        ico(balance, R.drawable.ic_wallet); make3d(balance)
        root.addView(balance, mlp())

        val grid = LinearLayout(this); grid.orientation = LinearLayout.HORIZONTAL
        val income = tv("Masuk\n${money(masuk)}", 16f, true)
        val expense = tv("Keluar\n${money(keluar)}", 16f, true)
        income.setTextColor(Color.rgb(22,163,74)); expense.setTextColor(Color.rgb(220,38,38))
        for (v in arrayOf(income, expense)) {
            v.setPadding(dp(14), dp(16), dp(14), dp(16)); v.background = solid(Color.WHITE, 20); make3d(v)
        }
        ico(income, R.drawable.ic_in); ico(expense, R.drawable.ic_out)
        val gl = LinearLayout.LayoutParams(0, -2, 1f); gl.rightMargin = dp(6)
        val gr = LinearLayout.LayoutParams(0, -2, 1f); gr.leftMargin = dp(6)
        grid.addView(income, gl); grid.addView(expense, gr)
        root.addView(grid, mlp())

        val sum = LinearLayout(this); sum.orientation = LinearLayout.VERTICAL
        sum.setPadding(dp(16), dp(12), dp(16), dp(12))
        sum.background = solid(Color.WHITE, 22); sum.elevation = dp(4).toFloat()
        sum.addView(tv("Ringkasan", 19f, true))
        sum.addView(tv("Sisa yang dapat ditabung: ${money((masuk-keluar).coerceAtLeast(0))}", 16f))
        val ring = RingChart(this)
        ring.setTarget(if (masuk > 0) keluar.toFloat() / masuk else 0f)
        val rp = LinearLayout.LayoutParams(dp(180), dp(180)); rp.gravity = Gravity.CENTER_HORIZONTAL; rp.topMargin = dp(8)
        sum.addView(ring, rp)
        root.addView(sum, mlp())

        root.addView(menuBtn("Tambah transaksi", R.drawable.ic_add) { showTransaction() }, mlp())
        root.addView(menuBtn("Laporan, ringkasan & kategori", R.drawable.ic_chart) { showReport() }, mlp())
        root.addView(menuBtn("Pengaturan", R.drawable.ic_settings) { showSettings() }, mlp())
        root.addView(menuBtn("Reset data", R.drawable.ic_trash) {
            AlertDialog.Builder(this).setMessage("Hapus semua transaksi?")
                .setPositiveButton("Hapus") { _, _ -> txs.clear(); persist(); recalc(); showDashboard() }
                .setNegativeButton("Batal", null).show()
        }, mlp())
        show(scrolled(root), forward, animate)
    }

    // ---------- Latar belakang (foto dari galeri) ----------
    private fun loadBackground() {
        val f = File(filesDir, "bg.jpg")
        bgBitmap = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    private fun applyBackground() {
        val b = bgBitmap
        if (b != null) {
            container.background = LayerDrawable(arrayOf<Drawable>(
                BitmapDrawable(resources, b), ColorDrawable(Color.argb(95, 255, 255, 255))))
        } else {
            container.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(224,242,254), Color.rgb(250,245,255)))
        }
    }

    private fun resetBackground() {
        File(filesDir, "bg.jpg").delete()
        bgBitmap = null
        applyBackground()
    }

    // Foto dipotong tengah sesuai rasio layar, diputar sesuai EXIF, lalu disalin ke penyimpanan aplikasi
    private fun importBackground(uri: Uri): Boolean {
        return try {
            val opt = BitmapFactory.Options(); opt.inJustDecodeBounds = true
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opt) }
            val m = resources.displayMetrics
            val tw = minOf(m.widthPixels, 1080)
            val th = tw * m.heightPixels / m.widthPixels
            var sample = 1
            while (opt.outWidth / (sample * 2) >= tw && opt.outHeight / (sample * 2) >= th) sample *= 2
            val o2 = BitmapFactory.Options(); o2.inSampleSize = sample
            var bmp = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o2) } ?: return false
            val rot = try {
                contentResolver.openInputStream(uri).use { st ->
                    when (ExifInterface(st!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            } catch (e: Throwable) { 0f }
            if (rot != 0f) {
                val mx = Matrix(); mx.postRotate(rot)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mx, true)
            }
            val ratio = tw.toFloat() / th
            var cw = bmp.width
            var ch = (cw / ratio).toInt()
            if (ch > bmp.height) { ch = bmp.height; cw = (ch * ratio).toInt() }
            val crop = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
            val out = Bitmap.createScaledBitmap(crop, tw, th, true)
            File(filesDir, "bg.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bgBitmap = out
            true
        } catch (e: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null && importBackground(uri)) {
                applyBackground()
                Toast.makeText(this, "Latar belakang diperbarui", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Foto tidak dapat dibuka", Toast.LENGTH_SHORT).show()
            }
            showSettings(animate = false)
        }
    }

    // ---------- Pengaturan: judul dan latar belakang ----------
    private fun showSettings(forward: Boolean = true, animate: Boolean = true) {
        onDashboard = false
        val root = page()
        root.addView(tv("Pengaturan", 26f, true))

        val c1 = LinearLayout(this); c1.orientation = LinearLayout.VERTICAL
        c1.setPadding(dp(16), dp(12), dp(16), dp(12))
        c1.background = solid(Color.WHITE, 22); c1.elevation = dp(4).toFloat()
        c1.addView(tv("Judul di bagian atas", 17f, true))
        val title = EditText(this); title.setText(judul); title.hint = "Contoh: Dompet Abdilah"
        title.setSingleLine()
        c1.addView(title)
        val saveT = Button(this); saveT.text = "Simpan judul"; styleBtn(saveT); ico(saveT, R.drawable.ic_check)
        saveT.setOnClickListener {
            judul = title.text.toString().trim().ifEmpty { "Dompet" }
            prefs.edit().putString("judul", judul).apply()
            Toast.makeText(this, "Judul tersimpan", Toast.LENGTH_SHORT).show()
        }
        c1.addView(saveT, mlp())
        root.addView(c1, mlp())

        val c2 = LinearLayout(this); c2.orientation = LinearLayout.VERTICAL
        c2.setPadding(dp(16), dp(12), dp(16), dp(12))
        c2.background = solid(Color.WHITE, 22); c2.elevation = dp(4).toFloat()
        c2.addView(tv("Latar belakang", 17f, true))
        val prev = ImageView(this)
        prev.scaleType = ImageView.ScaleType.CENTER_CROP
        prev.background = bg(Color.rgb(224,242,254), Color.rgb(250,245,255), 16)
        prev.outlineProvider = ViewOutlineProvider.BACKGROUND
        prev.clipToOutline = true
        bgBitmap?.let { prev.setImageBitmap(it) }
        c2.addView(prev, LinearLayout.LayoutParams(-1, dp(150)))
        val pick = Button(this); pick.text = "Pilih foto dari galeri"; styleBtn(pick); ico(pick, R.drawable.ic_image)
        pick.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            i.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(Intent.createChooser(i, "Pilih foto latar"), 1001)
        }
        c2.addView(pick, mlp())
        if (bgBitmap != null) {
            val clr = Button(this); clr.text = "Kembalikan latar bawaan"; styleBtn(clr); ico(clr, R.drawable.ic_trash)
            clr.setOnClickListener {
                resetBackground()
                Toast.makeText(this, "Latar bawaan dipulihkan", Toast.LENGTH_SHORT).show()
                showSettings(animate = false)
            }
            c2.addView(clr, mlp())
        }
        root.addView(c2, mlp())

        val back = Button(this); back.text = "Kembali"; ico(back, R.drawable.ic_back); styleBtn(back)
        back.setOnClickListener { showDashboard(forward = false) }
        root.addView(back, mlp())
        show(scrolled(root), forward, animate)
    }

    // ---------- Tambah transaksi ----------
    private fun showTransaction() {
        onDashboard = false
        val root = page()
        root.addView(tv("Tambah transaksi",26f,true))
        val type=Spinner(this)
        type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,arrayOf("Uang masuk","Uang keluar"))
        root.addView(type)
        val amount=EditText(this); amount.hint="Nominal (Rp)"; amount.inputType=2
        root.addView(amount)
        val cats = arrayOf("Makanan","Transport","Belanja","Tagihan","Hiburan","Kesehatan","Gaji","Lainnya")
        val category=AutoCompleteTextView(this); category.hint="Kategori, contoh: Makanan"
        category.setAdapter(ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,cats))
        category.threshold = 1
        category.setOnClickListener { category.showDropDown() }
        root.addView(category)
        val note=EditText(this); note.hint="Catatan"
        root.addView(note)
        val saveBtn=Button(this); saveBtn.text="Simpan"; ico(saveBtn, R.drawable.ic_check); styleBtn(saveBtn)
        saveBtn.setOnClickListener {
            val n=amount.text.toString().toLongOrNull()
            if(n==null || n<=0){ amount.error="Masukkan nominal yang benar"; return@setOnClickListener }
            val c=category.text.toString().trim().lowercase().replaceFirstChar { it.uppercase() }.ifEmpty { "Lainnya" }
            txs.add(Tx(type.selectedItemPosition==1, n, c, note.text.toString().trim(), System.currentTimeMillis()))
            persist(); recalc(); showDashboard(forward = false)
            Toast.makeText(this,"Transaksi tersimpan",Toast.LENGTH_SHORT).show()
        }
        root.addView(saveBtn, mlp())
        val back=Button(this); back.text="Batal"; ico(back, R.drawable.ic_close); styleBtn(back); back.setOnClickListener { showDashboard(forward = false) }
        root.addView(back, mlp())
        show(scrolled(root), true, true)
    }

    // ---------- Laporan: grafik donat per kategori + riwayat ----------
    private fun showReport(forward: Boolean = true, animate: Boolean = true) {
        onDashboard = false
        val root = page()
        root.addView(tv("Laporan & kategori",26f,true))
        val mo = monthly()
        root.addView(tv("Ringkasan 6 bulan terakhir",16f,true))
        val bars = BarChart3D(this)
        bars.background = solid(Color.WHITE, 22); bars.elevation = dp(4).toFloat()
        bars.setData(Array(mo.size) { mo[it].label }, LongArray(mo.size) { mo[it].inc }, LongArray(mo.size) { mo[it].out })
        root.addView(bars, LinearLayout.LayoutParams(-1, dp(260)))
        root.addView(tv("Geser diagram dengan jari untuk memutar 360°", 12f))
        val lg = LinearLayout(this); lg.orientation = LinearLayout.HORIZONTAL; lg.gravity = Gravity.CENTER_HORIZONTAL
        val l1 = tv("●  Masuk      ", 13f); l1.setTextColor(Color.rgb(22,163,74))
        val l2 = tv("●  Keluar", 13f); l2.setTextColor(Color.rgb(220,38,38))
        lg.addView(l1); lg.addView(l2)
        root.addView(lg)
        val ex = tv("Penjelasan ringkasan\n\n" + explain(mo), 14f)
        ex.setPadding(dp(14), dp(12), dp(14), dp(12)); ex.background = solid(Color.rgb(255,251,235), 16)
        val ep = LinearLayout.LayoutParams(-1, -2); ep.topMargin = dp(8)
        root.addView(ex, ep)
        val h2 = tv("Pengeluaran per kategori", 16f, true); h2.setPadding(0, dp(16), 0, 8)
        root.addView(h2)

        val sums = LinkedHashMap<String, Long>()
        for (t in txs) if (t.out) sums[t.cat] = (sums[t.cat] ?: 0L) + t.amount
        val sorted = sums.entries.sortedByDescending { it.value }

        val donut = DonutChart(this)
        donut.setData(FloatArray(sorted.size) { sorted[it].value.toFloat() },
                      IntArray(sorted.size) { palette[it % palette.size] })
        val lp = LinearLayout.LayoutParams(dp(210), dp(210))
        lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8); lp.bottomMargin = dp(8)
        root.addView(donut, lp)

        for ((i, e) in sorted.withIndex()) {
            val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val dot = tv("●", 16f); dot.setTextColor(palette[i % palette.size]); dot.setPadding(0,8,dp(8),8)
            val pct = Math.round(e.value * 100f / keluar)
            row.addView(dot)
            row.addView(tv(e.key, 16f), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(tv("$pct% • ${money(e.value)}", 14f))
            root.addView(row)
        }

        val head = tv("Riwayat transaksi", 19f, true); head.setPadding(0, dp(16), 0, 8)
        root.addView(head)
        if (txs.isEmpty()) root.addView(tv("Belum ada transaksi.", 14f))
        else root.addView(tv("Tahan lama pada transaksi untuk menghapus.", 12f))
        for (t in txs.asReversed()) root.addView(txRow(t))

        val back=Button(this); back.text="Kembali"; ico(back, R.drawable.ic_back); styleBtn(back); back.setOnClickListener { showDashboard(forward = false) }
        root.addView(back, mlp())
        show(scrolled(root), forward, animate)
    }

    private fun monthly(): List<Mo> {
        val names = arrayOf("Jan","Feb","Mar","Apr","Mei","Jun","Jul","Agu","Sep","Okt","Nov","Des")
        val cal = Calendar.getInstance()
        val nowIdx = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
        val inc = LongArray(6); val out = LongArray(6)
        for (t in txs) {
            cal.timeInMillis = t.time
            val b = 5 - (nowIdx - (cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)))
            if (b in 0..5) { if (t.out) out[b] += t.amount else inc[b] += t.amount }
        }
        return List(6) { Mo(names[(nowIdx - 5 + it) % 12], inc[it], out[it]) }
    }

    // Penjelasan otomatis dari hasil ringkasan
    private fun explain(mo: List<Mo>): String {
        if (txs.isEmpty()) return "Belum ada transaksi. Catat pemasukan dan pengeluaran pertama Anda untuk melihat ringkasan."
        val sb = StringBuilder()
        if (masuk > 0) {
            val r = Math.round(keluar * 100f / masuk)
            val nilai = when {
                r <= 50 -> "sangat sehat"
                r <= 80 -> "cukup sehat"
                r <= 100 -> "mendekati batas, perlu dikendalikan"
                else -> "melebihi pemasukan (defisit)"
            }
            sb.append("• Pengeluaran mencapai $r% dari total pemasukan: kondisi $nilai.\n")
        } else {
            sb.append("• Belum ada pemasukan tercatat, rasio pengeluaran belum bisa dihitung.\n")
        }
        if (saldo >= 0) sb.append("• Sisa dana saat ini ${money(saldo)}.\n")
        else sb.append("• Saldo negatif ${money(-saldo)}: pengeluaran lebih besar dari pemasukan.\n")
        val top = txs.filter { it.out }.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
        if (top != null && keluar > 0) {
            val p = Math.round(top.value * 100f / keluar)
            sb.append("• Pengeluaran terbesar: ${top.key} ($p% dari total pengeluaran, ${money(top.value)})")
            if (p >= 40) sb.append(". Porsinya dominan, cek apakah bisa dihemat.\n") else sb.append(".\n")
        }
        val cur = mo[5].out; val prev = mo[4].out
        if (prev > 0) {
            val ch = Math.round((cur - prev) * 100f / prev)
            if (ch > 0) sb.append("• Pengeluaran bulan ini naik $ch% dibanding bulan lalu.\n")
            else if (ch < 0) sb.append("• Pengeluaran bulan ini turun ${-ch}% dibanding bulan lalu.\n")
            else sb.append("• Pengeluaran bulan ini sama dengan bulan lalu.\n")
        }
        return sb.toString().trim()
    }

    private fun txRow(t: Tx): View {
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(6), 0, dp(6))
        val left = LinearLayout(this); left.orientation = LinearLayout.VERTICAL
        left.addView(tv(t.cat, 16f, true))
        val sub = tv((if (t.note.isEmpty()) "" else t.note + " • ") + dateFmt.format(Date(t.time)), 12f)
        sub.setTextColor(Color.rgb(100,116,139))
        left.addView(sub)
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val amt = tv((if (t.out) "- " else "+ ") + money(t.amount), 15f, true)
        amt.setTextColor(if (t.out) Color.rgb(220,38,38) else Color.rgb(22,163,74))
        row.addView(amt)
        row.setOnLongClickListener {
            AlertDialog.Builder(this).setMessage("Hapus transaksi ini?")
                .setPositiveButton("Hapus") { _, _ -> txs.remove(t); persist(); recalc(); showReport(animate = false) }
                .setNegativeButton("Batal", null).show()
            true
        }
        return row
    }
}

// ---------- Grafik (onDraw tanpa alokasi objek; animasi lewat ValueAnimator) ----------
private abstract class AnimatedView(ctx: Context) : View(ctx) {
    protected var progress = 0f
    private var anim: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800; startDelay = 120; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    protected fun fit(oval: RectF, stroke: Float) {
        val side = minOf(width, height) - stroke
        val l = (width - side) / 2f
        val t = (height - side) / 2f
        oval.set(l, t, l + side, t + side)
    }
}

private class RingChart(ctx: Context) : AnimatedView(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private var target = 0f
    private var lastPct = -1
    private var text = "0%"

    init {
        val d = ctx.resources.displayMetrics.density
        track.style = Paint.Style.STROKE; track.strokeWidth = 16 * d; track.color = Color.rgb(226,232,240)
        arc.style = Paint.Style.STROKE; arc.strokeWidth = 16 * d; arc.strokeCap = Paint.Cap.ROUND
        label.textAlign = Paint.Align.CENTER; label.textSize = 30 * d; label.isFakeBoldText = true
        label.color = Color.rgb(20,30,45)
        sub.textAlign = Paint.Align.CENTER; sub.textSize = 12 * d; sub.color = Color.rgb(100,116,139)
    }

    fun setTarget(p: Float) {
        target = p.coerceIn(0f, 1f)
        arc.color = if (target > 0.8f) Color.rgb(220,38,38) else Color.rgb(37,99,235)
    }

    override fun onDraw(c: Canvas) {
        fit(oval, track.strokeWidth)
        c.drawOval(oval, track)
        val v = target * progress
        c.drawArc(oval, -90f, 360f * v, false, arc)
        val pct = (v * 100).toInt()
        if (pct != lastPct) { lastPct = pct; text = "$pct%" }
        val cy = height / 2f
        c.drawText(text, width / 2f, cy - (label.ascent() + label.descent()) / 2f, label)
        c.drawText("dari pemasukan", width / 2f, cy + track.strokeWidth * 0.9f, sub)
    }
}

private class DonutChart(ctx: Context) : AnimatedView(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private var values = FloatArray(0)
    private var colors = IntArray(0)
    private var total = 0f

    init {
        val d = ctx.resources.displayMetrics.density
        track.style = Paint.Style.STROKE; track.strokeWidth = 28 * d; track.color = Color.rgb(226,232,240)
        seg.style = Paint.Style.STROKE; seg.strokeWidth = 28 * d
        label.textAlign = Paint.Align.CENTER; label.textSize = 14 * d; label.color = Color.rgb(100,116,139)
    }

    fun setData(v: FloatArray, c: IntArray) { values = v; colors = c; total = v.sum() }

    override fun onDraw(c: Canvas) {
        fit(oval, track.strokeWidth)
        c.drawOval(oval, track)
        val ty = height / 2f - (label.ascent() + label.descent()) / 2f
        if (total <= 0f) { c.drawText("Belum ada data", width / 2f, ty, label); return }
        var start = -90f
        for (i in values.indices) {
            val sweep = 360f * values[i] / total * progress
            seg.color = colors[i]
            c.drawArc(oval, start, sweep, false, seg)
            start += sweep
        }
        c.drawText("Pengeluaran", width / 2f, ty, label)
    }
}

// Diagram batang berkelompok: pemasukan (hijau) vs pengeluaran (merah) per bulan
private class BarChart(ctx: Context) : AnimatedView(ctx) {
    private val pIn = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pOut = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private var labels = arrayOf<String>()
    private var ins = LongArray(0)
    private var outs = LongArray(0)
    private var max = 1f
    private val d = ctx.resources.displayMetrics.density

    init {
        pIn.color = Color.rgb(22,163,74); pOut.color = Color.rgb(220,38,38)
        axis.color = Color.rgb(203,213,225); axis.strokeWidth = 1.5f * d
        txt.color = Color.rgb(100,116,139); txt.textSize = 12 * d; txt.textAlign = Paint.Align.CENTER
    }

    fun setData(l: Array<String>, i: LongArray, o: LongArray) {
        labels = l; ins = i; outs = o
        max = maxOf(1L, ins.maxOrNull() ?: 0L, outs.maxOrNull() ?: 0L).toFloat()
    }

    override fun onDraw(c: Canvas) {
        val n = labels.size
        if (n == 0) return
        val left = 8 * d
        val top = 8 * d
        val bottom = height - 24 * d
        val slot = (width - 2 * left) / n
        val bw = slot * 0.32f
        c.drawLine(left, bottom, width - left, bottom, axis)
        for (k in 0 until n) {
            val cx = left + slot * (k + 0.5f)
            val hIn = (bottom - top) * (ins[k] / max) * progress
            val hOut = (bottom - top) * (outs[k] / max) * progress
            c.drawRect(cx - bw - d, bottom - hIn, cx - d, bottom, pIn)
            c.drawRect(cx + d, bottom - hOut, cx + bw + d, bottom, pOut)
            c.drawText(labels[k], cx, height - 6 * d, txt)
        }
    }
}

// Diagram batang 3D: seret dengan jari untuk memutar bebas 360 derajat (putaran + kemiringan), ada inersia.
// Digambar manual dengan proyeksi perspektif; buffer dialokasikan sekali sehingga onDraw tanpa alokasi.
private class BarChart3D(ctx: Context) : AnimatedView(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val floorP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var labels = arrayOf<String>()
    private var ins = LongArray(0)
    private var outs = LongArray(0)
    private var max = 1f
    private var yaw = 28f
    private var pitch = -24f
    private var vYaw = 0f
    private var vPitch = 0f
    private var lx = 0f
    private var ly = 0f
    private var tCy = 1f
    private var tSy = 0f
    private var tCp = 1f
    private var tSp = 0f
    private var tCx = 0f
    private var tCyc = 0f
    private var tS = 1f
    private val px = FloatArray(112)
    private val py = FloatArray(112)
    private val pz = FloatArray(112)
    private val keys = FloatArray(72)
    private val order = IntArray(72)
    private val nz = FloatArray(6)
    private val shade = FloatArray(6)
    private val faceIdx = arrayOf(
        intArrayOf(0, 1, 5, 4), intArrayOf(2, 3, 7, 6), intArrayOf(0, 2, 6, 4),
        intArrayOf(1, 3, 7, 5), intArrayOf(0, 1, 3, 2), intArrayOf(4, 5, 7, 6)
    )
    private val wnx = floatArrayOf(0f, 0f, -1f, 1f, 0f, 0f)
    private val wny = floatArrayOf(-1f, 1f, 0f, 0f, 0f, 0f)
    private val wnz = floatArrayOf(0f, 0f, 0f, 0f, -1f, 1f)
    private val colorIn = Color.rgb(22, 163, 74)
    private val colorOut = Color.rgb(239, 68, 68)

    private val spin = object : Runnable {
        override fun run() {
            yaw += vYaw; pitch += vPitch
            vYaw *= 0.94f; vPitch *= 0.94f
            invalidate()
            if (abs(vYaw) + abs(vPitch) > 0.05f) postOnAnimation(this)
        }
    }

    init {
        fill.style = Paint.Style.FILL
        floorP.style = Paint.Style.FILL; floorP.color = Color.argb(60, 100, 116, 139)
        txt.color = Color.rgb(71, 85, 105); txt.textSize = 12 * d; txt.textAlign = Paint.Align.CENTER
    }

    fun setData(l: Array<String>, i: LongArray, o: LongArray) {
        labels = l; ins = i; outs = o
        max = maxOf(1L, ins.maxOrNull() ?: 0L, outs.maxOrNull() ?: 0L).toFloat()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                removeCallbacks(spin)
                lx = e.x; ly = e.y; vYaw = 0f; vPitch = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                vYaw = -(e.x - lx) * 0.5f; vPitch = -(e.y - ly) * 0.4f
                lx = e.x; ly = e.y
                yaw += vYaw; pitch += vPitch
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                postOnAnimation(spin)
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(spin)
        super.onDetachedFromWindow()
    }

    private fun proj(i: Int, wx: Float, wy: Float, wz: Float) {
        val x1 = wx * tCy + wz * tSy
        val z1 = -wx * tSy + wz * tCy
        val y2 = wy * tCp - z1 * tSp
        val z2 = wy * tSp + z1 * tCp
        val k = 22f / (22f + z2)
        px[i] = tCx + x1 * k * tS
        py[i] = tCyc - y2 * k * tS
        pz[i] = z2
    }

    override fun onDraw(c: Canvas) {
        val n = labels.size
        if (n == 0) return
        val ry = Math.toRadians(yaw.toDouble()).toFloat()
        val rp = Math.toRadians(pitch.toDouble()).toFloat()
        tCy = cos(ry); tSy = sin(ry); tCp = cos(rp); tSp = sin(rp)
        tCx = width / 2f; tCyc = height / 2f
        tS = minOf(width / 8.6f, height / 6.6f)

        // normal tiap sisi setelah diputar + pencahayaan
        for (f in 0 until 6) {
            val x1 = wnx[f] * tCy + wnz[f] * tSy
            val z1 = -wnx[f] * tSy + wnz[f] * tCy
            val y2 = wny[f] * tCp - z1 * tSp
            val z2 = wny[f] * tSp + z1 * tCp
            nz[f] = z2
            shade[f] = 0.5f + 0.5f * maxOf(0f, x1 * -0.4f + y2 * 0.7f + z2 * -0.6f)
        }

        // lantai dan label bulan
        val fw = n * 1.1f / 2f + 0.3f
        proj(96, -fw, -1.4f, -1.5f); proj(97, fw, -1.4f, -1.5f)
        proj(98, fw, -1.4f, 1.5f); proj(99, -fw, -1.4f, 1.5f)
        for (k in 0 until n) proj(100 + k, (k - (n - 1) / 2f) * 1.1f, -1.4f, -1.7f)
        path.reset()
        path.moveTo(px[96], py[96]); path.lineTo(px[97], py[97]); path.lineTo(px[98], py[98]); path.lineTo(px[99], py[99])
        path.close()
        c.drawPath(path, floorP)

        // sudut tiap balok (baris depan = masuk, baris belakang = keluar)
        val total = 2 * n
        for (b in 0 until total) {
            val k = if (b < n) b else b - n
            val v = if (b < n) ins[k] else outs[k]
            val h = 3.2f * (v / max) * progress
            val xa = (k - (n - 1) / 2f) * 1.1f - 0.38f
            val xb = xa + 0.76f
            val za = (if (b < n) -0.75f else 0.75f) - 0.4f
            val zb = za + 0.8f
            for (i in 0 until 8) {
                val wx = if (i and 1 != 0) xb else xa
                val wy = if (i and 2 != 0) h - 1.4f else -1.4f
                val wz = if (i and 4 != 0) zb else za
                proj(b * 8 + i, wx, wy, wz)
            }
        }

        // kumpulkan sisi yang menghadap kamera, urutkan dari terjauh (painter's algorithm)
        var cnt = 0
        for (b in 0 until total) {
            for (f in 0 until 6) {
                if (nz[f] >= 0f) continue
                val fi = faceIdx[f]
                val base = b * 8
                keys[cnt] = (pz[base + fi[0]] + pz[base + fi[1]] + pz[base + fi[2]] + pz[base + fi[3]]) / 4f
                order[cnt] = b * 6 + f
                var j = cnt
                while (j > 0 && keys[j - 1] < keys[j]) {
                    val tk = keys[j]; keys[j] = keys[j - 1]; keys[j - 1] = tk
                    val to = order[j]; order[j] = order[j - 1]; order[j - 1] = to
                    j--
                }
                cnt++
            }
        }
        for (q in 0 until cnt) {
            val id = order[q]
            val b = id / 6
            val f = id % 6
            val fi = faceIdx[f]
            val base = b * 8
            path.reset()
            path.moveTo(px[base + fi[0]], py[base + fi[0]])
            path.lineTo(px[base + fi[1]], py[base + fi[1]])
            path.lineTo(px[base + fi[2]], py[base + fi[2]])
            path.lineTo(px[base + fi[3]], py[base + fi[3]])
            path.close()
            val col = if (b < n) colorIn else colorOut
            val sh = shade[f]
            fill.color = Color.rgb((Color.red(col) * sh).toInt(), (Color.green(col) * sh).toInt(), (Color.blue(col) * sh).toInt())
            c.drawPath(path, fill)
        }
        for (k in 0 until n) c.drawText(labels[k], px[100 + k], py[100 + k], txt)
    }
}
