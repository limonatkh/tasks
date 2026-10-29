# اتفاق الربط بين «مخطط حياتي» وتطبيق القرآن — الإصدار 1

هذا الملف هو العقد الذي بُني عليه جانب تطبيق المهام (`com.imad.planner`). على تطبيق القرآن (`ps.imad.quran`) أن ينفّذ الجانب الموصوف هنا بنفس الأسماء حتى يعمل الربط.
كل الثوابت في تطبيق المهام موجودة في مكان واحد: `QuranContract` داخل `app/src/main/java/com/imad/planner/QuranLink.kt`.

## 1. الفكرة

- كل مهمة أو تذكير في تطبيق المهام يمكن ربطه بـ**ورد** في تطبيق القرآن، مع **نوع نشاط**: `read` (قراءة) أو `memorize` (حفظ) أو `review` (مراجعة).
- عند ضغط المستخدم «بدء» أو ضغطه على التذكير، يفتح تطبيق المهام تطبيق القرآن على هذا الورد. تطبيق القرآن يفتح الورد **عند آخر موضع محفوظ فيه**. تطبيق المهام لا يرسل رقم صفحة أبداً.
- تطبيق القرآن هو **مصدر الحقيقة** للتقدّم. تطبيق المهام يقرأ التقرير ويعرضه فقط، ولا يستنتج الإنجاز من الوقت ولا من فتح التطبيق.
- كل شيء محلي على الجهاز: لا إنترنت ولا حساب ولا خادم.

## 2. فتح ورد (تطبيق المهام ← تطبيق القرآن)

يرسل تطبيق المهام `Intent` صريحاً موجّهاً للحزمة `ps.imad.quran`:

| الحقل | القيمة |
|---|---|
| Action | `ps.imad.quran.action.OPEN_WARD` |
| Package | `ps.imad.quran` |
| Flags | `FLAG_ACTIVITY_NEW_TASK` |

الإضافات (Extras):

| المفتاح | النوع | الوصف |
|---|---|---|
| `protocol_version` | Int | دائماً `1` في هذا الإصدار |
| `mode` | String | `read` أو `memorize` أو `review` |
| `ward_id` | String | المعرّف الثابت للورد (لا يتغيّر إذا تغيّر الاسم) |
| `task_id` | String | معرّف المهمة في تطبيق المهام |
| `occurrence_id` | String | نص غير شفاف: `<task_id>@<YYYY-MM-DD>@<HHMM>` (وقت الموعد إن وُجد)، فيختلف بين تذكيرين لنفس المهمة في اليوم نفسه |
| `request_id` | String | UUID جديد لكل ضغطة؛ لمنع معالجة نفس الطلب مرتين |
| `caller_package` | String | `com.imad.planner` |

المطلوب من تطبيق القرآن:

1. نشاط (Activity) مُصدَّر يستقبل الـ Action أعلاه (`<intent-filter>` مع `category.DEFAULT`).
2. إذا كان `protocol_version` أكبر مما يدعمه: يفتح شاشته الرئيسية ويعرض رسالة «نسخة الربط غير مدعومة».
3. إذا لم يوجد `ward_id`: يعرض «الورد غير موجود» ولا يفتح ورداً آخر.
4. إذا تكرر `request_id`: يتجاهل الطلب الثاني (لا جلسة جديدة).
5. يفتح الورد على آخر موضع محفوظ له، بنوع النشاط `mode`.
6. يحفظ `task_id` و`occurrence_id` مع الجلسة التي تبدأ، حتى تظهر في تقرير الجلسات.
7. **فتح الورد ليس إنجازاً**: لا يسجّل قراءة أو حفظاً لمجرد الفتح أو البقاء على الصفحة.

ملاحظة: تطبيق المهام لا يفتح تطبيق القرآن تلقائياً عند حلول موعد التذكير؛ فقط عند ضغط المستخدم.

## 3. قراءة البيانات (تطبيق المهام ← ContentProvider في تطبيق القرآن)

### 3.1 الـ Provider والتفويض

- Authority: `ps.imad.quran.integration`
- يعرّف تطبيق القرآن صلاحية:

```xml
<permission
    android:name="ps.imad.quran.permission.INTEGRATION"
    android:protectionLevel="dangerous"
    android:label="قراءة تقدّم القرآن"
    android:description="@string/integration_permission_desc" />

<provider
    android:name=".integration.IntegrationProvider"
    android:authorities="ps.imad.quran.integration"
    android:exported="true"
    android:readPermission="ps.imad.quran.permission.INTEGRATION" />
```

- `dangerous` يعني أن المستخدم يوافق بنفسه (نافذة نظام) عندما يضغط «السماح» في تطبيق المهام. لا نفترض أن التطبيقين موقّعان بنفس المفتاح.
- تطبيق المهام يعلن `<uses-permission android:name="ps.imad.quran.permission.INTEGRATION"/>` و`<queries><package android:name="ps.imad.quran"/></queries>`.
- مهم: إذا ثُبّت تطبيق المهام **قبل** تطبيق القرآن، فلن يحصل على الصلاحية تلقائياً؛ هو يطلبها وقت الحاجة، وهذا مُنفّذ.

### 3.2 شكل كل الردود

كل مسار يرجع `Cursor` فيه **عمود واحد** اسمه `json` (TEXT) و**صف واحد**، قيمته كائن JSON.
كل طلب يحمل البارامتر `protocol_version=1`.

خطأ:

```json
{ "error": "unsupported_version" | "not_found" | "bad_request" | "internal", "message": "نص اختياري" }
```

### 3.3 المسارات

**`content://ps.imad.quran.integration/capabilities?protocol_version=1`**

```json
{
  "protocol_version": 1,
  "app_version": "1.4.0",
  "capabilities": ["open_ward", "wards", "report", "sessions", "progress_broadcast"],
  "timezone": "Asia/Gaza"
}
```

**`content://ps.imad.quran.integration/wards?protocol_version=1`**

```json
{
  "wards": [
    { "ward_id": "w_7f3a", "name": "قراءة الظهر", "mode": "read" },
    { "ward_id": "w_19bc", "name": "قراءة المغرب", "mode": "read" },
    { "ward_id": "w_a001", "name": "حفظ البقرة", "mode": "memorize" },
    { "ward_id": "w_a002", "name": "حفظ جزء عم", "mode": "memorize" },
    { "ward_id": "w_r001", "name": "مراجعة البقرة", "mode": "review" }
  ]
}
```

`ward_id` ثابت مدى حياة الورد. الاسم للعرض فقط.

**`content://ps.imad.quran.integration/report?protocol_version=1&day=2026-10-29`**

```json
{
  "protocol_version": 1,
  "day": "2026-10-29",
  "timezone": "Asia/Gaza",
  "report_version": 37,
  "generated_at": 1793280000000,
  "totals": {
    "read_ayahs": 40,
    "read_pages": 4,
    "memorized_ayahs": 5,
    "review_ayahs": 60,
    "review_plays": 95,
    "listen_seconds": 1830
  },
  "wards": [
    { "ward_id": "w_7f3a", "name": "قراءة الظهر", "mode": "read",
      "totals": { "read_ayahs": 20, "read_pages": 2, "memorized_ayahs": 0, "review_ayahs": 0, "review_plays": 0, "listen_seconds": 0 } },
    { "ward_id": "w_a001", "name": "حفظ البقرة", "mode": "memorize",
      "totals": { "read_ayahs": 0, "read_pages": 0, "memorized_ayahs": 5, "review_ayahs": 0, "review_plays": 0, "listen_seconds": 0 } },
    { "ward_id": "w_r001", "name": "مراجعة البقرة", "mode": "review",
      "totals": { "read_ayahs": 0, "read_pages": 0, "memorized_ayahs": 0, "review_ayahs": 60, "review_plays": 95, "listen_seconds": 1830 } }
  ]
}
```

قواعد الحساب (مسؤولية تطبيق القرآن):

- `read_ayahs` / `read_pages`: فقط ما **أكّد** المستخدم قراءته. فتح الصفحة أو البقاء عليها لا يُحسب.
- `memorized_ayahs`: الآيات التي سجّلها كمحفوظة اليوم. إذا ألغى التسجيل، تنقص في التقرير التالي.
- الاستماع أثناء الحفظ **لا** يُحسب حفظاً جديداً.
- `review_ayahs`: عدد الآيات **المختلفة** التي اكتمل تشغيلها. `review_plays`: عدد مرات التشغيل (التكرار يزيده). `listen_seconds`: مدة التشغيل الفعلي، بدون وقت الإيقاف المؤقت.
- `report_version`: رقم يزيد كلما تغيّر تقرير هذا اليوم. التقرير **يُستبدل** كاملاً ولا يُجمع فوق القديم.
- `day` يُحسب حسب `timezone` المتفق عليها (الافتراضي `Asia/Gaza`). كل الأوقات بـ epoch milliseconds (UTC).

**`content://ps.imad.quran.integration/sessions?protocol_version=1&day=2026-10-29`** (اختياري، غير مستخدم في العرض الحالي)

```json
{
  "sessions": [
    { "session_id": "s_88d1", "ward_id": "w_7f3a", "mode": "read",
      "started_at": 1793270000000, "ended_at": 1793271200000,
      "task_id": "4b1e…", "occurrence_id": "4b1e…@2026-10-29", "request_id": "c2a9…",
      "read_ayahs": 20, "read_pages": 2, "memorized_ayahs": 0, "review_ayahs": 0, "review_plays": 0, "listen_seconds": 0 }
  ]
}
```

`session_id` فريد وثابت؛ يستعمله أي طرف لمنع التكرار.

## 4. إشعار تغيّر التقدّم (تطبيق القرآن ← تطبيق المهام)

عند أي تغيير في تقدّم اليوم، يرسل تطبيق القرآن Broadcast موجّهاً:

```kotlin
context.sendBroadcast(
    Intent("ps.imad.quran.action.PROGRESS_CHANGED")
        .setPackage("com.imad.planner")
        .putExtra("protocol_version", 1)
        .putExtra("day", "2026-10-29")
        .putExtra("report_version", 37)
)
```

تطبيق المهام عند الاستقبال **يسحب** التقرير من الـ Provider فقط (لا يثق بمحتوى الإشعار). لذلك لا خطر إن أرسله تطبيق آخر.
تطبيق المهام لا يعتمد على هذا الإشعار وحده: يسحب التقرير أيضاً عند فتحه أو العودة إليه، وفيه زر «تحديث» يدوي.

## 5. ما نُفّذ في تطبيق المهام (الإصدار 0.11)

- (0.11) نشاط الفتح في تطبيق القرآن محمي بنفس الصلاحية؛ تطبيق المهام يطلبها قبل الفتح. إذا تعذّر الفتح لا يبدأ مؤقت المهمة، ويعرض السبب مع خيار «ابدأ بدون القرآن».

- طبقة الاتصال `QuranContract` + `QuranAdapter` (فحص التثبيت، الصلاحية، الـ Provider، قراءة الأوراد والتقرير، بناء Intent الفتح).
- «ربط بورد قرآن» من تفاصيل المهمة: نوع النشاط + اختيار ورد من قائمة تطبيق القرآن. يُحفظ `ward_id` والاسم للعرض. يمكن تغيير الربط أو إلغاؤه بدون حذف المهمة، ويمكن ربط أكثر من مهمة بنفس الورد.
- عند «ابدأ» (أو السحب للبدء أو زر بدء في التذكير) لمهمة مربوطة: يبدأ المؤقت ويفتح الورد. الفتح لا يعلّم المهمة منجزة.
- مزامنة التقرير: عند العودة للتطبيق، عند استقبال `PROGRESS_CHANGED`، وبزر «تحديث». آخر تقرير يُحفظ محلياً ويُعرض عند تعذّر الاتصال. التقرير الأحدث (حسب `report_version`) يستبدل السابق.
- عند المزامنة تُحدَّث أسماء الأوراد المربوطة (فتغيير الاسم لا يكسر الربط). الورد المحذوف يُعلَّم «انحذف» ويُطلب اختيار ورد آخر بدل فتح ورد غيره.
- العرض: لوحة «القرآن اليوم» في التقارير (القراءة المؤكدة، الحفظ الجديد، المراجعة المسموعة: آيات مختلفة / مرات تشغيل / مدة، كل ورد ورقمه، وقت آخر مزامنة)، ورقم اليوم بجانب كل مهمة مربوطة.
- الحالات غير المتاحة تُعرض كسبب واضح بدون انهيار: غير مثبت / «بانتظار تحديث تطبيق القرآن الداعم للربط» / الصلاحية غير ممنوحة / تعذّر الاتصال. لا توجد بيانات تجريبية تُعرض كأنها حقيقية.

## 6. ما يجب إضافته في تطبيق القرآن

1. تعريف الصلاحية `ps.imad.quran.permission.INTEGRATION` (dangerous).
2. نشاط يستقبل `ps.imad.quran.action.OPEN_WARD` بالقواعد في القسم 2.
3. `ContentProvider` بسلطة `ps.imad.quran.integration` محمي بالصلاحية، بالمسارات `capabilities` و`wards` و`report` (و`sessions` اختيارياً)، بالشكل في القسم 3.
4. حساب التقرير بالقواعد في 3.3، مع `report_version` متزايد لكل يوم.
5. إرسال `PROGRESS_CHANGED` الموجّه لـ `com.imad.planner` عند تغيّر التقدّم.
6. معرّفات ورد ثابتة لا تتغير بتغيير الاسم.

## 7. اختبارات متفق عليها

- تذكير الظهر والمغرب المربوطان بوردين مختلفين يفتحان الوردين الصحيحين.
- العودة من تطبيق القرآن ثم المزامنة مرتين لا تضاعف أي رقم (استبدال لا جمع).
- الضغط مرتين على نفس التذكير يرسل `request_id` مختلفاً لكل ضغطة، لكن تطبيق القرآن لا يبدأ جلستين لنفس الضغطة المكررة من النظام.
- تغيير اسم الورد لا يكسر الربط.
- غياب تطبيق القرآن أو عدم دعمه للربط أو رفض الصلاحية لا يسبب انهياراً.
- المهام غير المربوطة تعمل كما كانت.
