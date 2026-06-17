// MealPlanner.java - Планировщик питания на Java (CLI + Swing GUI)
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MealPlanner {
    private static final String DATA_FILE = "meal_data.json";

    static class Food {
        String name;
        double calories;
        double protein;
        double fat;
        double carbs;
        String description;
        Food(String name, double calories, double protein, double fat, double carbs, String description) {
            this.name = name; this.calories = calories; this.protein = protein; this.fat = fat; this.carbs = carbs; this.description = description;
        }
    }

    static class MealPlanItem {
        int id;
        String foodName;
        double portion;
        String date;
        String mealType;
        String notes;
        MealPlanItem(int id, String foodName, double portion, String date, String mealType, String notes) {
            this.id = id; this.foodName = foodName; this.portion = portion; this.date = date; this.mealType = mealType; this.notes = notes;
        }
    }

    static class Data {
        List<Food> foods = new ArrayList<>();
        List<MealPlanItem> plans = new ArrayList<>();
        int nextId = 1;

        void load() {
            // Упрощённо: для реального проекта использовать Jackson
            // В данной версии оставляем заглушку
            try {
                String json = new String(Files.readAllBytes(Paths.get(DATA_FILE)));
                // Парсинг через простой подход (для демонстрации)
                // Для настоящей реализации использовать библиотеку.
                // В этой версии оставляем пустым.
            } catch (Exception e) { }
        }

        void save() {
            try (PrintWriter pw = new PrintWriter(DATA_FILE)) {
                pw.println("{");
                pw.println("  \"foods\": [");
                for (int i = 0; i < foods.size(); i++) {
                    Food f = foods.get(i);
                    pw.printf("    {\"name\":\"%s\",\"calories\":%.1f,\"protein\":%.1f,\"fat\":%.1f,\"carbs\":%.1f,\"description\":\"%s\"}%s\n",
                            f.name, f.calories, f.protein, f.fat, f.carbs, f.description, (i < foods.size()-1 ? "," : ""));
                }
                pw.println("  ],");
                pw.println("  \"plans\": [");
                for (int i = 0; i < plans.size(); i++) {
                    MealPlanItem p = plans.get(i);
                    pw.printf("    {\"id\":%d,\"foodName\":\"%s\",\"portion\":%.1f,\"date\":\"%s\",\"mealType\":\"%s\",\"notes\":\"%s\"}%s\n",
                            p.id, p.foodName, p.portion, p.date, p.mealType, p.notes, (i < plans.size()-1 ? "," : ""));
                }
                pw.println("  ],");
                pw.printf("  \"next_id\": %d\n", nextId);
                pw.println("}");
            } catch (IOException e) { }
        }
    }

    // ========== ЛОГИКА ==========
    static Food addFood(Data data, String name, double calories, double protein, double fat, double carbs, String description) {
        Food f = new Food(name, calories, protein, fat, carbs, description);
        data.foods.add(f);
        data.save();
        return f;
    }

    static Food getFood(Data data, String name) {
        for (Food f : data.foods) {
            if (f.name.equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    static List<String> suggestFoods(Data data, String prefix) {
        return data.foods.stream()
                .filter(f -> f.name.toLowerCase().startsWith(prefix.toLowerCase()))
                .map(f -> f.name)
                .collect(Collectors.toList());
    }

    static MealPlanItem addMealPlan(Data data, String foodName, double portion, String date, String mealType, String notes) {
        if (date == null || date.isEmpty()) date = LocalDate.now().toString();
        MealPlanItem item = new MealPlanItem(data.nextId++, foodName, portion, date, mealType, notes);
        data.plans.add(item);
        data.save();
        return item;
    }

    static boolean deletePlanItem(Data data, int id) {
        for (Iterator<MealPlanItem> it = data.plans.iterator(); it.hasNext(); ) {
            if (it.next().id == id) {
                it.remove();
                data.save();
                return true;
            }
        }
        return false;
    }

    static List<MealPlanItem> getPlanForDate(Data data, String dateStr) {
        return data.plans.stream()
                .filter(p -> p.date.equals(dateStr))
                .sorted(Comparator.comparing(p -> p.mealType))
                .collect(Collectors.toList());
    }

    static Map<String, Object> getDayStats(Data data, String dateStr) {
        List<MealPlanItem> items = getPlanForDate(data, dateStr);
        double totalCal = 0, totalProt = 0, totalFat = 0, totalCarbs = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (MealPlanItem p : items) {
            Food food = getFood(data, p.foodName);
            if (food != null) {
                double factor = p.portion;
                totalCal += food.calories * factor;
                totalProt += food.protein * factor;
                totalFat += food.fat * factor;
                totalCarbs += food.carbs * factor;
                Map<String, Object> det = new HashMap<>();
                det.put("mealType", p.mealType);
                det.put("food", food.name);
                det.put("portion", p.portion);
                det.put("calories", food.calories * factor);
                details.add(det);
            } else {
                Map<String, Object> det = new HashMap<>();
                det.put("mealType", p.mealType);
                det.put("food", p.foodName);
                det.put("portion", p.portion);
                det.put("calories", 0.0);
                det.put("warning", "Неизвестный продукт");
                details.add(det);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalCalories", totalCal);
        result.put("totalProtein", totalProt);
        result.put("totalFat", totalFat);
        result.put("totalCarbs", totalCarbs);
        result.put("details", details);
        return result;
    }

    static List<MealPlanItem> generateRandomMenu(Data data, String dateStr, double caloriesLimit) {
        if (data.foods.isEmpty()) return new ArrayList<>();
        List<Food> shuffled = new ArrayList<>(data.foods);
        Collections.shuffle(shuffled, new Random());
        List<MealPlanItem> items = new ArrayList<>();
        double totalCal = 0;
        String[] mealTypes = {"breakfast", "lunch", "dinner", "snack"};
        int idx = 0;
        for (Food food : shuffled) {
            double[] portionChoices = {0.5, 0.75, 1.0, 1.25, 1.5};
            double portion = portionChoices[new Random().nextInt(portionChoices.length)];
            double cal = food.calories * portion;
            if (totalCal + cal <= caloriesLimit) {
                String mtype = mealTypes[idx % mealTypes.length];
                idx++;
                MealPlanItem item = new MealPlanItem(data.nextId++, food.name, portion, dateStr, mtype, "Сгенерировано автоматически");
                data.plans.add(item);
                items.add(item);
                totalCal += cal;
            }
            if (totalCal >= caloriesLimit * 0.8) break;
        }
        data.save();
        return items;
    }

    static void exportCSV(Data data, String filepath, String dateStr) throws IOException {
        try (PrintWriter pw = new PrintWriter(filepath)) {
            pw.println("Date,Meal Type,Food,Portion,Calories,Protein,Fat,Carbs");
            List<MealPlanItem> plans = dateStr == null ? data.plans : getPlanForDate(data, dateStr);
            for (MealPlanItem p : plans) {
                Food food = getFood(data, p.foodName);
                double c = food == null ? 0 : food.calories * p.portion;
                double pr = food == null ? 0 : food.protein * p.portion;
                double fa = food == null ? 0 : food.fat * p.portion;
                double ca = food == null ? 0 : food.carbs * p.portion;
                pw.printf("%s,%s,%s,%.1f,%.1f,%.1f,%.1f,%.1f\n",
                        p.date, p.mealType, p.foodName, p.portion, c, pr, fa, ca);
            }
        }
    }

    static void importCSV(Data data, String filepath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 8) continue;
                String date = parts[0];
                String mealType = parts[1];
                String foodName = parts[2];
                double portion = Double.parseDouble(parts[3]);
                addMealPlan(data, foodName, portion, date, mealType, "Импортировано из CSV");
            }
        }
    }

    // ========== CLI ==========
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--gui")) {
            SwingUtilities.invokeLater(() -> new MealPlannerGUI().setVisible(true));
            return;
        }
        Data data = new Data();
        data.load();
        if (args.length == 0) {
            interactiveMode(data);
            return;
        }
        try {
            String cmd = args[0];
            switch (cmd) {
                case "add-food": {
                    String name = null; double calories = 0, protein = 0, fat = 0, carbs = 0; String description = "";
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--name")) name = args[++i];
                        else if (args[i].equals("--calories")) calories = Double.parseDouble(args[++i]);
                        else if (args[i].equals("--protein")) protein = Double.parseDouble(args[++i]);
                        else if (args[i].equals("--fat")) fat = Double.parseDouble(args[++i]);
                        else if (args[i].equals("--carbs")) carbs = Double.parseDouble(args[++i]);
                        else if (args[i].equals("--description")) description = args[++i];
                    }
                    if (name == null) { System.out.println("Укажите --name"); return; }
                    Food f = addFood(data, name, calories, protein, fat, carbs, description);
                    System.out.printf("✅ Продукт '%s' добавлен\n", f.name);
                    break;
                }
                case "add-meal": {
                    String foodName = null; double portion = 1; String date = null; String mealType = "lunch"; String notes = "";
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--food")) foodName = args[++i];
                        else if (args[i].equals("--portion")) portion = Double.parseDouble(args[++i]);
                        else if (args[i].equals("--date")) date = args[++i];
                        else if (args[i].equals("--meal-type")) mealType = args[++i];
                        else if (args[i].equals("--notes")) notes = args[++i];
                    }
                    if (foodName == null) { System.out.println("Укажите --food"); return; }
                    if (getFood(data, foodName) == null) { System.out.println("Продукт не найден"); return; }
                    MealPlanItem item = addMealPlan(data, foodName, portion, date, mealType, notes);
                    System.out.printf("✅ Приём пищи #%d добавлен\n", item.id);
                    break;
                }
                case "list": {
                    String date = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--date")) date = args[++i];
                    }
                    if (date == null) date = LocalDate.now().toString();
                    List<MealPlanItem> items = getPlanForDate(data, date);
                    if (items.isEmpty()) { System.out.printf("Нет записей на %s\n", date); break; }
                    Map<String, Object> stats = getDayStats(data, date);
                    System.out.printf("📅 План на %s\n", date);
                    System.out.printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал");
                    for (MealPlanItem p : items) {
                        Food food = getFood(data, p.foodName);
                        double cal = food == null ? 0 : food.calories * p.portion;
                        System.out.printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", p.id, p.mealType, p.foodName, p.portion, cal);
                    }
                    System.out.printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n",
                            stats.get("totalCalories"), stats.get("totalProtein"), stats.get("totalFat"), stats.get("totalCarbs"));
                    break;
                }
                case "stats": {
                    String date = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--date")) date = args[++i];
                    }
                    if (date == null) date = LocalDate.now().toString();
                    Map<String, Object> stats = getDayStats(data, date);
                    System.out.printf("📊 Статистика за %s\n", date);
                    System.out.printf("Калории: %.1f\n", stats.get("totalCalories"));
                    System.out.printf("Белки: %.1f г\n", stats.get("totalProtein"));
                    System.out.printf("Жиры: %.1f г\n", stats.get("totalFat"));
                    System.out.printf("Углеводы: %.1f г\n", stats.get("totalCarbs"));
                    List<Map<String, Object>> details = (List<Map<String, Object>>) stats.get("details");
                    if (!details.isEmpty()) {
                        System.out.println("Детали:");
                        for (Map<String, Object> d : details) {
                            System.out.printf("  %s: %s x%.1f = %.1f ккал\n", d.get("mealType"), d.get("food"), d.get("portion"), d.get("calories"));
                        }
                    }
                    break;
                }
                case "delete": {
                    int id = 0;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--id")) id = Integer.parseInt(args[++i]);
                    }
                    if (id == 0) { System.out.println("Укажите --id"); return; }
                    if (deletePlanItem(data, id)) {
                        System.out.printf("✅ Пункт #%d удалён\n", id);
                    } else {
                        System.out.printf("❌ Пункт #%d не найден\n", id);
                    }
                    break;
                }
                case "random": {
                    String date = null; double limit = 2000;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--date")) date = args[++i];
                        else if (args[i].equals("--calories-limit")) limit = Double.parseDouble(args[++i]);
                    }
                    if (date == null) date = LocalDate.now().toString();
                    List<MealPlanItem> items = generateRandomMenu(data, date, limit);
                    System.out.printf("✅ Сгенерировано %d блюд на %s\n", items.size(), date);
                    break;
                }
                case "export": {
                    String output = null, date = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--output")) output = args[++i];
                        else if (args[i].equals("--date")) date = args[++i];
                    }
                    if (output == null) { System.out.println("Укажите --output"); return; }
                    exportCSV(data, output, date);
                    System.out.printf("Экспортировано в %s\n", output);
                    break;
                }
                case "import": {
                    String file = null;
                    for (int i = 1; i < args.length; i++) {
                        if (args[i].equals("--file")) file = args[++i];
                    }
                    if (file == null) { System.out.println("Укажите --file"); return; }
                    importCSV(data, file);
                    System.out.printf("Импортировано из %s\n", file);
                    break;
                }
                default:
                    interactiveMode(data);
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    static void interactiveMode(Data data) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n🍽️ Планировщик питания (интерактивный)");
            System.out.println("1. Добавить продукт");
            System.out.println("2. Добавить приём пищи");
            System.out.println("3. Показать план на день");
            System.out.println("4. Статистика за день");
            System.out.println("5. Удалить пункт плана");
            System.out.println("6. Сгенерировать случайное меню");
            System.out.println("7. Экспорт CSV");
            System.out.println("8. Импорт CSV");
            System.out.println("0. Выход");
            System.out.print("Выберите действие: ");
            String choice = sc.nextLine();
            switch (choice) {
                case "0": return;
                case "1":
                    System.out.print("Название продукта: ");
                    String name = sc.nextLine();
                    if (name.isEmpty()) { System.out.println("Название обязательно"); break; }
                    System.out.print("Калории: ");
                    double cal = Double.parseDouble(sc.nextLine());
                    System.out.print("Белки (г): ");
                    double prot = Double.parseDouble(sc.nextLine());
                    System.out.print("Жиры (г): ");
                    double fat = Double.parseDouble(sc.nextLine());
                    System.out.print("Углеводы (г): ");
                    double carbs = Double.parseDouble(sc.nextLine());
                    System.out.print("Описание (необязательно): ");
                    String desc = sc.nextLine();
                    addFood(data, name, cal, prot, fat, carbs, desc);
                    System.out.printf("✅ Продукт '%s' добавлен\n", name);
                    break;
                case "2":
                    System.out.print("Название продукта: ");
                    String fname = sc.nextLine();
                    if (fname.isEmpty()) { System.out.println("Введите название"); break; }
                    if (getFood(data, fname) == null) { System.out.println("Продукт не найден"); break; }
                    System.out.print("Количество порций (по умолчанию 1): ");
                    double portion = 1;
                    String portStr = sc.nextLine();
                    if (!portStr.isEmpty()) portion = Double.parseDouble(portStr);
                    System.out.print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                    String date = sc.nextLine();
                    if (date.isEmpty()) date = LocalDate.now().toString();
                    System.out.print("Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ");
                    String mealType = sc.nextLine();
                    if (mealType.isEmpty()) mealType = "lunch";
                    System.out.print("Заметки: ");
                    String notes = sc.nextLine();
                    MealPlanItem item = addMealPlan(data, fname, portion, date, mealType, notes);
                    System.out.printf("✅ Добавлен приём пищи #%d\n", item.id);
                    break;
                case "3":
                    System.out.print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                    String d = sc.nextLine();
                    if (d.isEmpty()) d = LocalDate.now().toString();
                    List<MealPlanItem> items = getPlanForDate(data, d);
                    if (items.isEmpty()) { System.out.printf("Нет записей на %s\n", d); break; }
                    Map<String, Object> stats = getDayStats(data, d);
                    System.out.printf("📅 План на %s\n", d);
                    System.out.printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал");
                    for (MealPlanItem p : items) {
                        Food food = getFood(data, p.foodName);
                        double cal2 = food == null ? 0 : food.calories * p.portion;
                        System.out.printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", p.id, p.mealType, p.foodName, p.portion, cal2);
                    }
                    System.out.printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n",
                            stats.get("totalCalories"), stats.get("totalProtein"), stats.get("totalFat"), stats.get("totalCarbs"));
                    break;
                case "4":
                    System.out.print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                    String d2 = sc.nextLine();
                    if (d2.isEmpty()) d2 = LocalDate.now().toString();
                    Map<String, Object> stats2 = getDayStats(data, d2);
                    System.out.printf("📊 Статистика за %s\n", d2);
                    System.out.printf("Калории: %.1f\n", stats2.get("totalCalories"));
                    System.out.printf("Белки: %.1f г\n", stats2.get("totalProtein"));
                    System.out.printf("Жиры: %.1f г\n", stats2.get("totalFat"));
                    System.out.printf("Углеводы: %.1f г\n", stats2.get("totalCarbs"));
                    List<Map<String, Object>> details = (List<Map<String, Object>>) stats2.get("details");
                    if (!details.isEmpty()) {
                        System.out.println("Детали:");
                        for (Map<String, Object> det : details) {
                            System.out.printf("  %s: %s x%.1f = %.1f ккал\n", det.get("mealType"), det.get("food"), det.get("portion"), det.get("calories"));
                        }
                    }
                    break;
                case "5":
                    System.out.print("ID пункта для удаления: ");
                    int id = Integer.parseInt(sc.nextLine());
                    if (deletePlanItem(data, id)) {
                        System.out.println("✅ Удалено");
                    } else {
                        System.out.println("❌ Не найдено");
                    }
                    break;
                case "6":
                    System.out.print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                    String d3 = sc.nextLine();
                    if (d3.isEmpty()) d3 = LocalDate.now().toString();
                    System.out.print("Лимит калорий (по умолчанию 2000): ");
                    double limit = 2000;
                    String limStr = sc.nextLine();
                    if (!limStr.isEmpty()) limit = Double.parseDouble(limStr);
                    List<MealPlanItem> generated = generateRandomMenu(data, d3, limit);
                    System.out.printf("✅ Сгенерировано %d блюд\n", generated.size());
                    break;
                case "7":
                    System.out.print("Имя файла (CSV): ");
                    String file = sc.nextLine();
                    if (file.isEmpty()) file = "meal_plan.csv";
                    System.out.print("Дата (оставьте пустым для всех): ");
                    String dateExp = sc.nextLine();
                    try {
                        exportCSV(data, file, dateExp.isEmpty() ? null : dateExp);
                        System.out.printf("Экспортировано в %s\n", file);
                    } catch (IOException e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                    break;
                case "8":
                    System.out.print("Имя файла (CSV): ");
                    String fileImp = sc.nextLine();
                    if (fileImp.isEmpty()) { System.out.println("Укажите файл"); break; }
                    try {
                        importCSV(data, fileImp);
                        System.out.printf("Импортировано из %s\n", fileImp);
                    } catch (IOException e) {
                        System.out.println("Ошибка: " + e.getMessage());
                    }
                    break;
                default:
                    System.out.println("Неверный выбор");
            }
        }
    }

    // ========== GUI ==========
    static class MealPlannerGUI extends JFrame {
        private Data data = new Data();
        private JTable table;
        private DefaultTableModel model;
        private JTextField dateField, foodField, portionField, notesField;
        private JComboBox<String> mealTypeCombo;

        public MealPlannerGUI() {
            data.load();
            setTitle("🍽️ Планировщик питания");
            setSize(800, 500);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout(5,5));
            JPanel top = new JPanel(new FlowLayout());
            top.add(new JLabel("Дата:"));
            dateField = new JTextField(10);
            dateField.setText(LocalDate.now().toString());
            top.add(dateField);
            JButton refreshBtn = new JButton("Обновить");
            refreshBtn.addActionListener(e -> refreshTable());
            top.add(refreshBtn);
            JButton todayBtn = new JButton("Сегодня");
            todayBtn.addActionListener(e -> { dateField.setText(LocalDate.now().toString()); refreshTable(); });
            top.add(todayBtn);
            add(top, BorderLayout.NORTH);

            model = new DefaultTableModel(new String[]{"ID","Приём","Продукт","Порция","Ккал"}, 0);
            table = new JTable(model);
            add(new JScrollPane(table), BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout());
            JButton addBtn = new JButton("Добавить приём");
            addBtn.addActionListener(e -> addMealDialog());
            bottom.add(addBtn);
            JButton deleteBtn = new JButton("🗑 Удалить");
            deleteBtn.addActionListener(e -> deleteMeal());
            bottom.add(deleteBtn);
            JButton statsBtn = new JButton("📊 Статистика");
            statsBtn.addActionListener(e -> showStats());
            bottom.add(statsBtn);
            JButton randomBtn = new JButton("🎲 Случайное меню");
            randomBtn.addActionListener(e -> randomMenu());
            bottom.add(randomBtn);
            JButton exportBtn = new JButton("💾 Экспорт CSV");
            exportBtn.addActionListener(e -> exportCSV());
            bottom.add(exportBtn);
            add(bottom, BorderLayout.SOUTH);

            refreshTable();
        }

        void refreshTable() {
            model.setRowCount(0);
            String d = dateField.getText();
            List<MealPlanItem> items = getPlanForDate(data, d);
            Map<String, Object> stats = getDayStats(data, d);
            for (MealPlanItem p : items) {
                Food food = getFood(data, p.foodName);
                double cal = food == null ? 0 : food.calories * p.portion;
                model.addRow(new Object[]{p.id, p.mealType, p.foodName, p.portion, cal});
            }
            // Обновим заголовок с итогами
            setTitle(String.format("🍽️ Планировщик питания - %s (Итого: %.1f ккал)", d, stats.get("totalCalories")));
        }

        void addMealDialog() {
            JDialog dialog = new JDialog(this, "Добавить приём", true);
            dialog.setLayout(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5,5,5,5);
            gbc.gridx = 0; gbc.gridy = 0; dialog.add(new JLabel("Продукт:"), gbc);
            gbc.gridx = 1; foodField = new JTextField(15); dialog.add(foodField, gbc);
            gbc.gridx = 0; gbc.gridy = 1; dialog.add(new JLabel("Порция:"), gbc);
            gbc.gridx = 1; portionField = new JTextField(5); portionField.setText("1.0"); dialog.add(portionField, gbc);
            gbc.gridx = 0; gbc.gridy = 2; dialog.add(new JLabel("Тип приёма:"), gbc);
            gbc.gridx = 1; mealTypeCombo = new JComboBox<>(new String[]{"breakfast","lunch","dinner","snack"}); dialog.add(mealTypeCombo, gbc);
            gbc.gridx = 0; gbc.gridy = 3; dialog.add(new JLabel("Заметки:"), gbc);
            gbc.gridx = 1; notesField = new JTextField(15); dialog.add(notesField, gbc);
            gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
            JButton saveBtn = new JButton("Сохранить");
            saveBtn.addActionListener(e -> {
                String fname = foodField.getText().trim();
                if (fname.isEmpty()) { JOptionPane.showMessageDialog(dialog, "Введите продукт"); return; }
                if (getFood(data, fname) == null) {
                    JOptionPane.showMessageDialog(dialog, "Продукт не найден");
                    return;
                }
                double portion = Double.parseDouble(portionField.getText().trim());
                String date = dateField.getText();
                String mealType = (String) mealTypeCombo.getSelectedItem();
                String notes = notesField.getText().trim();
                addMealPlan(data, fname, portion, date, mealType, notes);
                dialog.dispose();
                refreshTable();
            });
            dialog.add(saveBtn, gbc);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        void deleteMeal() {
            int row = table.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Выберите запись"); return; }
            int id = (int) model.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(this, "Удалить пункт?") == JOptionPane.YES_OPTION) {
                deletePlanItem(data, id);
                refreshTable();
            }
        }

        void showStats() {
            String d = dateField.getText();
            Map<String, Object> stats = getDayStats(data, d);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📊 Статистика за %s\n", d));
            sb.append(String.format("Калории: %.1f\n", stats.get("totalCalories")));
            sb.append(String.format("Белки: %.1f г\n", stats.get("totalProtein")));
            sb.append(String.format("Жиры: %.1f г\n", stats.get("totalFat")));
            sb.append(String.format("Углеводы: %.1f г\n", stats.get("totalCarbs")));
            List<Map<String, Object>> details = (List<Map<String, Object>>) stats.get("details");
            if (!details.isEmpty()) {
                sb.append("Детали:\n");
                for (Map<String, Object> det : details) {
                    sb.append(String.format("  %s: %s x%.1f = %.1f ккал\n", det.get("mealType"), det.get("food"), det.get("portion"), det.get("calories")));
                }
            }
            JOptionPane.showMessageDialog(this, sb.toString());
        }

        void randomMenu() {
            String d = dateField.getText();
            String limitStr = JOptionPane.showInputDialog(this, "Лимит калорий:", "2000");
            double limit = 2000;
            try { limit = Double.parseDouble(limitStr); } catch (Exception e) {}
            generateRandomMenu(data, d, limit);
            refreshTable();
            JOptionPane.showMessageDialog(this, "Сгенерировано случайное меню");
        }

        void exportCSV() {
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    exportCSV(data, fc.getSelectedFile().getAbsolutePath(), dateField.getText());
                    JOptionPane.showMessageDialog(this, "Экспортировано");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
                }
            }
        }
    }
}
