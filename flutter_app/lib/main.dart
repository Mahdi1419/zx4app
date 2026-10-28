import 'dart:ui';
import 'package:flutter/material.dart';

void main() => runApp(const SmsModuleApp());

class SmsModuleApp extends StatefulWidget {
  const SmsModuleApp({super.key});
  @override
  State<SmsModuleApp> createState() => _SmsModuleAppState();
}

class _SmsModuleAppState extends State<SmsModuleApp> {
  ThemeMode mode = ThemeMode.system;
  Color accent = const Color(0xFFF59E0B);
  String appTitle = 'کنترل ماژول پیامکی';
  String deviceName = 'ماژول پیامکی';
  int gap = 30;

  ThemeData theme(Brightness brightness) {
    final dark = brightness == Brightness.dark;
    final fg = dark ? const Color(0xFFF4F4F6) : const Color(0xFF202127);
    return ThemeData(
      useMaterial3: true, brightness: brightness,
      colorScheme: ColorScheme.fromSeed(seedColor: accent, brightness: brightness,
        surface: dark ? const Color(0xFF202126) : const Color(0xFFFDFDFF)),
      scaffoldBackgroundColor: dark ? const Color(0xFF101114) : const Color(0xFFF2F3F7),
      textTheme: ThemeData(brightness: brightness).textTheme.apply(bodyColor: fg, displayColor: fg),
    );
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false, title: appTitle, themeMode: mode,
    theme: theme(Brightness.light), darkTheme: theme(Brightness.dark),
    home: Directionality(textDirection: TextDirection.rtl, child: SmsHome(
      title: appTitle, device: deviceName, gap: gap, accent: accent, mode: mode,
      onTitle: (v) => setState(() => appTitle = v),
      onDevice: (v) => setState(() => deviceName = v),
      onGap: (v) => setState(() => gap = v),
      onAccent: (v) => setState(() => accent = v),
      onMode: (v) => setState(() => mode = v),
    )),
  );
}

class SmsHome extends StatefulWidget {
  const SmsHome({super.key, required this.title, required this.device, required this.gap,
    required this.accent, required this.mode, required this.onTitle, required this.onDevice,
    required this.onGap, required this.onAccent, required this.onMode});
  final String title, device;
  final int gap;
  final Color accent;
  final ThemeMode mode;
  final ValueChanged<String> onTitle, onDevice;
  final ValueChanged<int> onGap;
  final ValueChanged<Color> onAccent;
  final ValueChanged<ThemeMode> onMode;
  @override
  State<SmsHome> createState() => _SmsHomeState();
}

class _SmsHomeState extends State<SmsHome> {
  int tab = 0;
  final List<List<String>> commands = [
    ['روشن کردن همه', '44', 'power'],
    ['خاموش کردن همه', '00', 'off'],
    ['وضعیت رله‌ها', '333', 'sensors'],
    ['پمپ آب', '21 → 20', 'water'],
  ];
  final List<String> history = [];

  @override
  Widget build(BuildContext context) => Scaffold(
    extendBody: true,
    body: Stack(children: [
      const Positioned.fill(child: Ambient()),
      SafeArea(child: IndexedStack(index: tab, children: [home(), schedules(), logs(), settings()])),
    ]),
    bottomNavigationBar: SafeArea(minimum: const EdgeInsets.fromLTRB(14, 0, 14, 10),
      child: Glass(child: Row(children: [
        nav(0, Icons.grid_view_rounded, 'خانه'),
        nav(1, Icons.calendar_month_rounded, 'زمان‌بندی'),
        nav(2, Icons.receipt_long_rounded, 'گزارش‌ها'),
        nav(3, Icons.tune_rounded, 'تنظیمات'),
      ]))),
    floatingActionButton: tab == 0 ? FloatingActionButton.extended(
      onPressed: newCommand, icon: const Icon(Icons.add_rounded), label: const Text('فرمان جدید')) : null,
  );

  Widget nav(int i, IconData icon, String label) => Expanded(child: Padding(
    padding: const EdgeInsets.all(4), child: InkWell(
      borderRadius: BorderRadius.circular(18), onTap: () => setState(() => tab = i),
      child: AnimatedContainer(duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(color: tab == i ? widget.accent.withOpacity(.16) : Colors.transparent,
          borderRadius: BorderRadius.circular(18)),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, color: tab == i ? widget.accent : Theme.of(context).colorScheme.onSurfaceVariant),
          const SizedBox(height: 3), Text(label, style: Theme.of(context).textTheme.labelSmall?.copyWith(
            color: tab == i ? widget.accent : Theme.of(context).colorScheme.onSurfaceVariant)),
        ]),
      ),
    ),
  ));

  Widget home() => ListView(key: const ValueKey('home'), padding: const EdgeInsets.fromLTRB(18, 18, 18, 110), children: [
    Row(children: [
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('کنترل هوشمند', style: TextStyle(color: widget.accent, fontWeight: FontWeight.w700)),
        const SizedBox(height: 5), Text(widget.title, style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
      ])),
      Container(width: 54, height: 54, decoration: BoxDecoration(
        gradient: LinearGradient(colors: [widget.accent, widget.accent.withOpacity(.4)]),
        borderRadius: BorderRadius.circular(20)), child: const Icon(Icons.sms_rounded, color: Color(0xFF202126), size: 29)),
    ]),
    const SizedBox(height: 22),
    Glass(padding: const EdgeInsets.all(20), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('وضعیت ماژول', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
          const SizedBox(height: 6), Text(widget.device, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          const SizedBox(height: 12), Chip(avatar: Icon(Icons.link_off_rounded, size: 16, color: widget.accent),
            label: const Text('پل پیامکی در انتظار اتصال'), side: BorderSide.none,
            backgroundColor: widget.accent.withOpacity(.12)),
        ])),
        Container(width: 58, height: 58, decoration: BoxDecoration(
          color: widget.accent.withOpacity(.15), borderRadius: BorderRadius.circular(20)),
          child: Icon(Icons.sms_rounded, color: widget.accent, size: 30)),
      ]),
      Row(children: [
        Expanded(child: metric('فرمان‌ها', commands.length.toString(), Icons.bolt_rounded)),
        const SizedBox(width: 10), Expanded(child: metric('فاصله اجباری', widget.gap.toString() + ' ثانیه', Icons.timer_outlined)),
      ]),
    ])),
    const SizedBox(height: 24),
    Row(children: [Expanded(child: Text('فرمان‌های سریع', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800))),
      Text(commands.length.toString() + ' فرمان', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant))]),
    const SizedBox(height: 12),
    ...commands.map(commandCard),
    const SizedBox(height: 10),
    Glass(child: ListTile(leading: Icon(Icons.info_outline_rounded, color: widget.accent),
      title: const Text('نسخه رابط کاربری Flutter'),
      subtitle: const Text('ارسال واقعی SMS هنوز به پل بومی اندروید متصل نشده است. اجرای فرمان در این نسخه آزمایشی است.'))),
  ]);

  Widget metric(String label, String value, IconData icon) => Padding(
    padding: const EdgeInsets.only(top: 18), child: Glass(padding: const EdgeInsets.all(12),
      child: Row(children: [Icon(icon, color: widget.accent), const SizedBox(width: 8),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(value, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800)),
          Text(label, style: Theme.of(context).textTheme.labelMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
        ]))])),
  );

  Widget commandCard(List<String> c) => Padding(padding: const EdgeInsets.only(bottom: 10),
    child: Glass(child: ListTile(
      leading: Container(width: 46, height: 46, decoration: BoxDecoration(
        color: widget.accent.withOpacity(.14), borderRadius: BorderRadius.circular(16)),
        child: Icon(c[2] == 'water' ? Icons.water_drop_rounded : c[2] == 'sensors' ? Icons.sensors_rounded : Icons.power_settings_new_rounded, color: widget.accent)),
      title: Text(c[0], style: const TextStyle(fontWeight: FontWeight.w700)),
      subtitle: Text('کد پیامک: ' + c[1]),
      trailing: IconButton(tooltip: 'اجرای فرمان', onPressed: () {
        setState(() => history.insert(0, 'آزمایشی: ' + c[0] + ' (' + c[1] + ')'));
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('دمو: پیامک واقعی ارسال نشد.')));
      }, icon: Icon(Icons.play_circle_fill_rounded, color: widget.accent, size: 34)),
    )));

  Widget schedules() => ListView(padding: const EdgeInsets.all(20), children: [
    Text('زمان‌بندی‌ها', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
    const SizedBox(height: 12),
    const Glass(child: ListTile(leading: Icon(Icons.schedule_rounded), title: Text('زمان‌بندی سیستم'),
      subtitle: Text('برای اجرای واقعی، زمان‌بندی اندروید باید به پل بومی متصل شود.'))),
    const SizedBox(height: 12),
    const Glass(child: ListTile(leading: Icon(Icons.battery_charging_full_rounded), title: Text('اجرای پس‌زمینه'),
      subtitle: Text('AlarmManager و بازیابی پس از راه‌اندازی مجدد هنوز مهاجرت داده نشده‌اند.'))),
  ]);

  Widget logs() => ListView(padding: const EdgeInsets.all(20), children: [
    Row(children: [Expanded(child: Text('گزارش‌ها', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800))),
      if (history.isNotEmpty) TextButton(onPressed: () => setState(history.clear), child: const Text('پاک کردن'))]),
    const SizedBox(height: 12),
    if (history.isEmpty) Glass(padding: const EdgeInsets.all(28), child: Column(children: [
      Icon(Icons.receipt_long_rounded, color: widget.accent, size: 42), const SizedBox(height: 12),
      const Text('هنوز گزارشی ثبت نشده', style: TextStyle(fontWeight: FontWeight.w700)),
      const SizedBox(height: 5), const Text('گزارش ارسال و پاسخ‌ها پس از اتصال پل پیامکی نمایش داده می‌شوند.', textAlign: TextAlign.center),
    ])) else ...history.map((s) => Padding(padding: const EdgeInsets.only(bottom: 8),
      child: Glass(child: ListTile(leading: Icon(Icons.history_rounded, color: widget.accent), title: Text(s))))),
  ]);

  Widget settings() => ListView(padding: const EdgeInsets.fromLTRB(18, 18, 18, 120), children: [
    Text('تنظیمات', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w800)),
    const SizedBox(height: 5), Text('شخصی‌سازی ظاهر و فاصله ارسال', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
    const SizedBox(height: 18),
    Glass(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('شخصی‌سازی', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height: 14), textField('نام برنامه', widget.title, widget.onTitle),
      const SizedBox(height: 12), textField('نام دستگاه', widget.device, widget.onDevice),
      const SizedBox(height: 18), const Text('تم برنامه', style: TextStyle(fontWeight: FontWeight.w700)),
      const SizedBox(height: 8),
      SegmentedButton<ThemeMode>(segments: const [
        ButtonSegment(value: ThemeMode.system, label: Text('سیستم'), icon: Icon(Icons.phone_android)),
        ButtonSegment(value: ThemeMode.light, label: Text('روشن'), icon: Icon(Icons.light_mode_outlined)),
        ButtonSegment(value: ThemeMode.dark, label: Text('تیره'), icon: Icon(Icons.dark_mode_outlined)),
      ], selected: {widget.mode}, onSelectionChanged: (s) => widget.onMode(s.first)),
      const SizedBox(height: 18), const Text('رنگ تأکیدی', style: TextStyle(fontWeight: FontWeight.w700)),
      const SizedBox(height: 10), Wrap(spacing: 12, children: [
        const Color(0xFFF59E0B), const Color(0xFFB8A1FF), const Color(0xFF65C8D0), const Color(0xFFF28BA8),
      ].map((c) => GestureDetector(onTap: () => widget.onAccent(c), child: Container(
        width: 38, height: 38, decoration: BoxDecoration(color: c, shape: BoxShape.circle,
          border: Border.all(color: c == widget.accent ? Theme.of(context).colorScheme.onSurface : Colors.transparent, width: 3)),
        child: c == widget.accent ? const Icon(Icons.check, color: Color(0xFF202126)) : null,
      ))).toList()),
    ])),
    const SizedBox(height: 14),
    Glass(padding: const EdgeInsets.all(18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('فاصله اجباری ارسال', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
      const SizedBox(height: 6), Text('حداقل فاصله بین پیامک‌ها و مراحل یک فرمان.',
        style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
      const SizedBox(height: 16),
      Row(children: [const Expanded(child: Text('حداقل فاصله')), Text(widget.gap.toString() + ' ثانیه',
        style: TextStyle(color: widget.accent, fontWeight: FontWeight.w800))]),
      Slider(value: widget.gap.toDouble(), min: 1, max: 300, divisions: 299,
        label: widget.gap.toString() + ' ثانیه', onChanged: (v) => widget.onGap(v.round())),
      const Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text('۱ ثانیه'), Text('۵ دقیقه')]),
    ])),
    const SizedBox(height: 14),
    const Glass(child: ListTile(leading: Icon(Icons.phonelink_setup_rounded), title: Text('اتصال اندروید'),
      subtitle: Text('ارسال SMS، مجوزها و زمان‌بندی پس‌زمینه در این نمونه به موتور بومی متصل نیستند.'))),
  ]);

  Widget textField(String label, String initial, ValueChanged<String> onChanged) => TextFormField(
    key: ValueKey(label + initial), initialValue: initial, onChanged: onChanged,
    decoration: InputDecoration(labelText: label, border: OutlineInputBorder(borderRadius: BorderRadius.circular(16))),
  );

  Future<void> newCommand() async {
    final name = TextEditingController(), code = TextEditingController();
    final result = await showDialog<List<String>>(context: context, builder: (ctx) => AlertDialog(
      title: const Text('فرمان جدید'),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: name, decoration: const InputDecoration(labelText: 'نام فرمان')),
        const SizedBox(height: 12), TextField(controller: code, decoration: const InputDecoration(labelText: 'کد پیامک')),
      ]),
      actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('لغو')),
        FilledButton(onPressed: () {
          if (name.text.trim().isNotEmpty && code.text.trim().isNotEmpty) {
            Navigator.pop(ctx, [name.text.trim(), code.text.trim(), 'sms']);
          }
        }, child: const Text('افزودن'))],
    ));
    if (result != null && mounted) setState(() => commands.add(result));
  }
}

class Glass extends StatelessWidget {
  const Glass({super.key, required this.child, this.padding = const EdgeInsets.symmetric(vertical: 4)});
  final Widget child;
  final EdgeInsetsGeometry padding;
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return ClipRRect(borderRadius: BorderRadius.circular(25), child: BackdropFilter(
      filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
      child: Container(padding: padding, decoration: BoxDecoration(
        color: (dark ? const Color(0xFF292A31) : Colors.white).withOpacity(dark ? .82 : .72),
        borderRadius: BorderRadius.circular(25),
        border: Border.all(color: (dark ? Colors.white : const Color(0xFF747784)).withOpacity(.20)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(dark ? .10 : .035), blurRadius: 24, offset: const Offset(0, 9))],
      ), child: child),
    ));
  }
}

class Ambient extends StatelessWidget {
  const Ambient({super.key});
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Stack(children: [
      Positioned.fill(child: ColoredBox(color: Theme.of(context).scaffoldBackgroundColor)),
      Positioned(top: -100, right: -80, child: orb(const Color(0xFFF59E0B).withOpacity(dark ? .12 : .13), 260)),
      Positioned(top: 270, left: -110, child: orb(const Color(0xFFB8A1FF).withOpacity(dark ? .10 : .14), 250)),
      Positioned(bottom: 40, right: 80, child: orb(const Color(0xFF65C8D0).withOpacity(dark ? .06 : .10), 190)),
    ]);
  }
  Widget orb(Color color, double size) => ImageFiltered(
    imageFilter: ImageFilter.blur(sigmaX: 38, sigmaY: 38),
    child: Container(width: size, height: size, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
  );
}
