# ──────────────────────────────────────────────────────────────
#  Contax — قوانین R8 (از نسخه ۱.۰.۴ minify فعال است)
# ──────────────────────────────────────────────────────────────

# Room: موجودیت‌ها و DAO (قوانین پیش‌فرض Room هم همراه کتابخانه است؛ این keep محض احتیاط است)
-keep class com.contax.app.data.db.** { *; }

# خوانایی crash ها: شماره خط در stack trace حفظ شود
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# اعضای enum ها (mapping بین name و valueOf در ذخیره‌سازی تنظیمات استفاده می‌شود)
-keepclassmembers enum com.contax.app.** { *; }
