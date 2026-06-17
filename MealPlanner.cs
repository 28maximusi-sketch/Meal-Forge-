// MealPlanner.cs - Планировщик питания на C# (CLI + WinForms)
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Windows.Forms;

namespace MealPlanner
{
    public class Food
    {
        public string Name { get; set; }
        public double Calories { get; set; }
        public double Protein { get; set; }
        public double Fat { get; set; }
        public double Carbs { get; set; }
        public string Description { get; set; }
    }

    public class MealPlanItem
    {
        public int Id { get; set; }
        public string FoodName { get; set; }
        public double Portion { get; set; }
        public string Date { get; set; }
        public string MealType { get; set; }
        public string Notes { get; set; }
    }

    public class Data
    {
        public List<Food> Foods { get; set; } = new List<Food>();
        public List<MealPlanItem> Plans { get; set; } = new List<MealPlanItem>();
        public int NextId { get; set; } = 1;
        private const string DataFile = "meal_data.json";

        public void Load()
        {
            if (File.Exists(DataFile))
            {
                try
                {
                    string json = File.ReadAllText(DataFile);
                    var data = JsonSerializer.Deserialize<Data>(json);
                    if (data != null)
                    {
                        Foods = data.Foods;
                        Plans = data.Plans;
                        NextId = data.NextId;
                        return;
                    }
                }
                catch { }
            }
            Foods = new List<Food>();
            Plans = new List<MealPlanItem>();
            NextId = 1;
        }

        public void Save()
        {
            string json = JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(DataFile, json);
        }
    }

    public static class Planner
    {
        public static Food AddFood(Data data, string name, double calories, double protein, double fat, double carbs, string description)
        {
            var f = new Food { Name = name, Calories = calories, Protein = protein, Fat = fat, Carbs = carbs, Description = description };
            data.Foods.Add(f);
            data.Save();
            return f;
        }

        public static Food GetFood(Data data, string name)
        {
            return data.Foods.FirstOrDefault(f => f.Name.Equals(name, StringComparison.OrdinalIgnoreCase));
        }

        public static List<string> SuggestFoods(Data data, string prefix)
        {
            return data.Foods.Where(f => f.Name.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                             .Select(f => f.Name).ToList();
        }

        public static MealPlanItem AddMealPlan(Data data, string foodName, double portion, string date, string mealType, string notes)
        {
            if (string.IsNullOrEmpty(date)) date = DateTime.Now.ToString("yyyy-MM-dd");
            var item = new MealPlanItem
            {
                Id = data.NextId++,
                FoodName = foodName,
                Portion = portion,
                Date = date,
                MealType = mealType,
                Notes = notes
            };
            data.Plans.Add(item);
            data.Save();
            return item;
        }

        public static bool DeletePlanItem(Data data, int id)
        {
            int removed = data.Plans.RemoveAll(p => p.Id == id);
            if (removed > 0) { data.Save(); return true; }
            return false;
        }

        public static List<MealPlanItem> GetPlanForDate(Data data, string dateStr)
        {
            return data.Plans.Where(p => p.Date == dateStr).OrderBy(p => p.MealType).ToList();
        }

        public static (double totalCal, double totalProt, double totalFat, double totalCarbs, List<dynamic> details)
            GetDayStats(Data data, string dateStr)
        {
            var items = GetPlanForDate(data, dateStr);
            double totalCal = 0, totalProt = 0, totalFat = 0, totalCarbs = 0;
            var details = new List<dynamic>();
            foreach (var p in items)
            {
                var food = GetFood(data, p.FoodName);
                if (food != null)
                {
                    double factor = p.Portion;
                    totalCal += food.Calories * factor;
                    totalProt += food.Protein * factor;
                    totalFat += food.Fat * factor;
                    totalCarbs += food.Carbs * factor;
                    details.Add(new { mealType = p.MealType, food = food.Name, portion = p.Portion, calories = food.Calories * factor });
                }
                else
                {
                    details.Add(new { mealType = p.MealType, food = p.FoodName, portion = p.Portion, calories = 0.0, warning = "Неизвестный продукт" });
                }
            }
            return (totalCal, totalProt, totalFat, totalCarbs, details);
        }

        public static List<MealPlanItem> GenerateRandomMenu(Data data, string dateStr, double caloriesLimit)
        {
            if (!data.Foods.Any()) return new List<MealPlanItem>();
            var rnd = new Random();
            var shuffled = data.Foods.OrderBy(x => rnd.Next()).ToList();
            var items = new List<MealPlanItem>();
            double totalCal = 0;
            string[] mealTypes = { "breakfast", "lunch", "dinner", "snack" };
            int idx = 0;
            foreach (var food in shuffled)
            {
                double[] portionChoices = { 0.5, 0.75, 1.0, 1.25, 1.5 };
                double portion = portionChoices[rnd.Next(portionChoices.Length)];
                double cal = food.Calories * portion;
                if (totalCal + cal <= caloriesLimit)
                {
                    string mtype = mealTypes[idx % mealTypes.Length];
                    idx++;
                    var item = new MealPlanItem
                    {
                        Id = data.NextId++,
                        FoodName = food.Name,
                        Portion = portion,
                        Date = dateStr,
                        MealType = mtype,
                        Notes = "Сгенерировано автоматически"
                    };
                    data.Plans.Add(item);
                    items.Add(item);
                    totalCal += cal;
                }
                if (totalCal >= caloriesLimit * 0.8) break;
            }
            data.Save();
            return items;
        }

        public static void ExportCSV(Data data, string filepath, string dateStr)
        {
            using (var sw = new StreamWriter(filepath))
            {
                sw.WriteLine("Date,Meal Type,Food,Portion,Calories,Protein,Fat,Carbs");
                var plans = string.IsNullOrEmpty(dateStr) ? data.Plans : GetPlanForDate(data, dateStr);
                foreach (var p in plans)
                {
                    var food = GetFood(data, p.FoodName);
                    double c = food == null ? 0 : food.Calories * p.Portion;
                    double pr = food == null ? 0 : food.Protein * p.Portion;
                    double fa = food == null ? 0 : food.Fat * p.Portion;
                    double ca = food == null ? 0 : food.Carbs * p.Portion;
                    sw.WriteLine($"{p.Date},{p.MealType},{p.FoodName},{p.Portion},{c},{pr},{fa},{ca}");
                }
            }
        }

        public static void ImportCSV(Data data, string filepath)
        {
            var lines = File.ReadAllLines(filepath);
            for (int i = 1; i < lines.Length; i++)
            {
                var parts = lines[i].Split(',');
                if (parts.Length < 8) continue;
                string date = parts[0];
                string mealType = parts[1];
                string foodName = parts[2];
                double portion = double.Parse(parts[3]);
                AddMealPlan(data, foodName, portion, date, mealType, "Импортировано из CSV");
            }
        }
    }

    class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--gui")
            {
                Application.EnableVisualStyles();
                Application.Run(new MealPlannerGUI());
                return;
            }
            var data = new Data();
            data.Load();
            if (args.Length == 0)
            {
                InteractiveMode(data);
                return;
            }
            try
            {
                string cmd = args[0];
                switch (cmd)
                {
                    case "add-food":
                        string name = null; double calories = 0, protein = 0, fat = 0, carbs = 0; string desc = "";
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--name") name = args[++i];
                            else if (args[i] == "--calories") calories = double.Parse(args[++i]);
                            else if (args[i] == "--protein") protein = double.Parse(args[++i]);
                            else if (args[i] == "--fat") fat = double.Parse(args[++i]);
                            else if (args[i] == "--carbs") carbs = double.Parse(args[++i]);
                            else if (args[i] == "--description") desc = args[++i];
                        }
                        if (name == null) { Console.WriteLine("Укажите --name"); return; }
                        var f = Planner.AddFood(data, name, calories, protein, fat, carbs, desc);
                        Console.WriteLine($"✅ Продукт '{f.Name}' добавлен");
                        break;
                    case "add-meal":
                        string foodName = null; double portion = 1; string date = null; string mealType = "lunch"; string notes = "";
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--food") foodName = args[++i];
                            else if (args[i] == "--portion") portion = double.Parse(args[++i]);
                            else if (args[i] == "--date") date = args[++i];
                            else if (args[i] == "--meal-type") mealType = args[++i];
                            else if (args[i] == "--notes") notes = args[++i];
                        }
                        if (foodName == null) { Console.WriteLine("Укажите --food"); return; }
                        if (Planner.GetFood(data, foodName) == null) { Console.WriteLine("Продукт не найден"); return; }
                        var item = Planner.AddMealPlan(data, foodName, portion, date, mealType, notes);
                        Console.WriteLine($"✅ Приём пищи #{item.Id} добавлен");
                        break;
                    case "list":
                        string listDate = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--date") listDate = args[++i];
                        if (listDate == null) listDate = DateTime.Now.ToString("yyyy-MM-dd");
                        var list = Planner.GetPlanForDate(data, listDate);
                        if (!list.Any()) { Console.WriteLine($"Нет записей на {listDate}"); break; }
                        var stats = Planner.GetDayStats(data, listDate);
                        Console.WriteLine($"📅 План на {listDate}");
                        Console.WriteLine($"{"ID",-4} {"Приём",-10} {"Продукт",-20} {"Порция",-8} {"Ккал",-8}");
                        foreach (var p in list)
                        {
                            var food = Planner.GetFood(data, p.FoodName);
                            double cal = food == null ? 0 : food.Calories * p.Portion;
                            Console.WriteLine($"{p.Id,-4} {p.MealType,-10} {p.FoodName,-20} {p.Portion,-8:F1} {cal,-8:F1}");
                        }
                        Console.WriteLine($"Итого: {stats.totalCal:F1} ккал, Б: {stats.totalProt:F1}, Ж: {stats.totalFat:F1}, У: {stats.totalCarbs:F1}");
                        break;
                    case "stats":
                        string statDate = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--date") statDate = args[++i];
                        if (statDate == null) statDate = DateTime.Now.ToString("yyyy-MM-dd");
                        var stat = Planner.GetDayStats(data, statDate);
                        Console.WriteLine($"📊 Статистика за {statDate}");
                        Console.WriteLine($"Калории: {stat.totalCal:F1}");
                        Console.WriteLine($"Белки: {stat.totalProt:F1} г");
                        Console.WriteLine($"Жиры: {stat.totalFat:F1} г");
                        Console.WriteLine($"Углеводы: {stat.totalCarbs:F1} г");
                        if (stat.details.Any())
                        {
                            Console.WriteLine("Детали:");
                            foreach (var d in stat.details)
                            {
                                Console.WriteLine($"  {d.mealType}: {d.food} x{d.portion:F1} = {d.calories:F1} ккал");
                            }
                        }
                        break;
                    case "delete":
                        int delId = 0;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--id") delId = int.Parse(args[++i]);
                        if (delId == 0) { Console.WriteLine("Укажите --id"); return; }
                        if (Planner.DeletePlanItem(data, delId))
                            Console.WriteLine($"✅ Пункт #{delId} удалён");
                        else
                            Console.WriteLine($"❌ Пункт #{delId} не найден");
                        break;
                    case "random":
                        string rndDate = null; double limit = 2000;
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--date") rndDate = args[++i];
                            else if (args[i] == "--calories-limit") limit = double.Parse(args[++i]);
                        }
                        if (rndDate == null) rndDate = DateTime.Now.ToString("yyyy-MM-dd");
                        var generated = Planner.GenerateRandomMenu(data, rndDate, limit);
                        Console.WriteLine($"✅ Сгенерировано {generated.Count} блюд на {rndDate}");
                        break;
                    case "export":
                        string expFile = null, expDate = null;
                        for (int i = 1; i < args.Length; i++)
                        {
                            if (args[i] == "--output") expFile = args[++i];
                            else if (args[i] == "--date") expDate = args[++i];
                        }
                        if (expFile == null) { Console.WriteLine("Укажите --output"); return; }
                        Planner.ExportCSV(data, expFile, expDate);
                        Console.WriteLine($"Экспортировано в {expFile}");
                        break;
                    case "import":
                        string impFile = null;
                        for (int i = 1; i < args.Length; i++) if (args[i] == "--file") impFile = args[++i];
                        if (impFile == null) { Console.WriteLine("Укажите --file"); return; }
                        Planner.ImportCSV(data, impFile);
                        Console.WriteLine($"Импортировано из {impFile}");
                        break;
                    default:
                        InteractiveMode(data);
                        break;
                }
            }
            catch (Exception e)
            {
                Console.WriteLine($"Ошибка: {e.Message}");
            }
        }

        static void InteractiveMode(Data data)
        {
            while (true)
            {
                Console.WriteLine("\n🍽️ Планировщик питания (интерактивный)");
                Console.WriteLine("1. Добавить продукт");
                Console.WriteLine("2. Добавить приём пищи");
                Console.WriteLine("3. Показать план на день");
                Console.WriteLine("4. Статистика за день");
                Console.WriteLine("5. Удалить пункт плана");
                Console.WriteLine("6. Сгенерировать случайное меню");
                Console.WriteLine("7. Экспорт CSV");
                Console.WriteLine("8. Импорт CSV");
                Console.WriteLine("0. Выход");
                Console.Write("Выберите действие: ");
                string choice = Console.ReadLine();
                switch (choice)
                {
                    case "0": return;
                    case "1":
                        Console.Write("Название продукта: ");
                        string name = Console.ReadLine();
                        if (string.IsNullOrEmpty(name)) { Console.WriteLine("Название обязательно"); break; }
                        Console.Write("Калории: ");
                        double cal = double.Parse(Console.ReadLine());
                        Console.Write("Белки (г): ");
                        double prot = double.Parse(Console.ReadLine());
                        Console.Write("Жиры (г): ");
                        double fat = double.Parse(Console.ReadLine());
                        Console.Write("Углеводы (г): ");
                        double carbs = double.Parse(Console.ReadLine());
                        Console.Write("Описание (необязательно): ");
                        string desc = Console.ReadLine();
                        Planner.AddFood(data, name, cal, prot, fat, carbs, desc);
                        Console.WriteLine($"✅ Продукт '{name}' добавлен");
                        break;
                    case "2":
                        Console.Write("Название продукта: ");
                        string fname = Console.ReadLine();
                        if (string.IsNullOrEmpty(fname)) { Console.WriteLine("Введите название"); break; }
                        if (Planner.GetFood(data, fname) == null) { Console.WriteLine("Продукт не найден"); break; }
                        Console.Write("Количество порций (по умолчанию 1): ");
                        string portStr = Console.ReadLine();
                        double portion = string.IsNullOrEmpty(portStr) ? 1 : double.Parse(portStr);
                        Console.Write("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                        string date = Console.ReadLine();
                        if (string.IsNullOrEmpty(date)) date = DateTime.Now.ToString("yyyy-MM-dd");
                        Console.Write("Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ");
                        string mealType = Console.ReadLine();
                        if (string.IsNullOrEmpty(mealType)) mealType = "lunch";
                        Console.Write("Заметки: ");
                        string notes = Console.ReadLine();
                        var item = Planner.AddMealPlan(data, fname, portion, date, mealType, notes);
                        Console.WriteLine($"✅ Добавлен приём пищи #{item.Id}");
                        break;
                    case "3":
                        Console.Write("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                        string d = Console.ReadLine();
                        if (string.IsNullOrEmpty(d)) d = DateTime.Now.ToString("yyyy-MM-dd");
                        var items = Planner.GetPlanForDate(data, d);
                        if (!items.Any()) { Console.WriteLine($"Нет записей на {d}"); break; }
                        var stats = Planner.GetDayStats(data, d);
                        Console.WriteLine($"📅 План на {d}");
                        Console.WriteLine($"{"ID",-4} {"Приём",-10} {"Продукт",-20} {"Порция",-8} {"Ккал",-8}");
                        foreach (var p in items)
                        {
                            var food = Planner.GetFood(data, p.FoodName);
                            double cal2 = food == null ? 0 : food.Calories * p.Portion;
                            Console.WriteLine($"{p.Id,-4} {p.MealType,-10} {p.FoodName,-20} {p.Portion,-8:F1} {cal2,-8:F1}");
                        }
                        Console.WriteLine($"Итого: {stats.totalCal:F1} ккал, Б: {stats.totalProt:F1}, Ж: {stats.totalFat:F1}, У: {stats.totalCarbs:F1}");
                        break;
                    case "4":
                        Console.Write("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                        string d2 = Console.ReadLine();
                        if (string.IsNullOrEmpty(d2)) d2 = DateTime.Now.ToString("yyyy-MM-dd");
                        var stat = Planner.GetDayStats(data, d2);
                        Console.WriteLine($"📊 Статистика за {d2}");
                        Console.WriteLine($"Калории: {stat.totalCal:F1}");
                        Console.WriteLine($"Белки: {stat.totalProt:F1} г");
                        Console.WriteLine($"Жиры: {stat.totalFat:F1} г");
                        Console.WriteLine($"Углеводы: {stat.totalCarbs:F1} г");
                        if (stat.details.Any())
                        {
                            Console.WriteLine("Детали:");
                            foreach (var det in stat.details)
                                Console.WriteLine($"  {det.mealType}: {det.food} x{det.portion:F1} = {det.calories:F1} ккал");
                        }
                        break;
                    case "5":
                        Console.Write("ID пункта для удаления: ");
                        int id = int.Parse(Console.ReadLine());
                        if (Planner.DeletePlanItem(data, id))
                            Console.WriteLine("✅ Удалено");
                        else
                            Console.WriteLine("❌ Не найдено");
                        break;
                    case "6":
                        Console.Write("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                        string d3 = Console.ReadLine();
                        if (string.IsNullOrEmpty(d3)) d3 = DateTime.Now.ToString("yyyy-MM-dd");
                        Console.Write("Лимит калорий (по умолчанию 2000): ");
                        string limStr = Console.ReadLine();
                        double limit = string.IsNullOrEmpty(limStr) ? 2000 : double.Parse(limStr);
                        var generated = Planner.GenerateRandomMenu(data, d3, limit);
                        Console.WriteLine($"✅ Сгенерировано {generated.Count} блюд");
                        break;
                    case "7":
                        Console.Write("Имя файла (CSV): ");
                        string file = Console.ReadLine();
                        if (string.IsNullOrEmpty(file)) file = "meal_plan.csv";
                        Console.Write("Дата (оставьте пустым для всех): ");
                        string expDate = Console.ReadLine();
                        Planner.ExportCSV(data, file, expDate);
                        Console.WriteLine($"Экспортировано в {file}");
                        break;
                    case "8":
                        Console.Write("Имя файла (CSV): ");
                        string impFile = Console.ReadLine();
                        if (string.IsNullOrEmpty(impFile)) { Console.WriteLine("Укажите файл"); break; }
                        Planner.ImportCSV(data, impFile);
                        Console.WriteLine($"Импортировано из {impFile}");
                        break;
                    default:
                        Console.WriteLine("Неверный выбор");
                        break;
                }
            }
        }
    }

    // ========== GUI ==========
    public class MealPlannerGUI : Form
    {
        private Data data = new Data();
        private DataGridView grid;
        private TextBox dateBox, foodBox, portionBox, notesBox;
        private ComboBox mealTypeBox;

        public MealPlannerGUI()
        {
            data.Load();
            Text = "🍽️ Планировщик питания";
            Size = new System.Drawing.Size(800, 500);
            StartPosition = FormStartPosition.CenterScreen;

            var top = new FlowLayoutPanel { Dock = DockStyle.Top, Padding = new Padding(5) };
            top.Controls.Add(new Label { Text = "Дата:", AutoSize = true });
            dateBox = new TextBox { Width = 100, Text = DateTime.Now.ToString("yyyy-MM-dd") };
            top.Controls.Add(dateBox);
            var refreshBtn = new Button { Text = "Обновить" };
            refreshBtn.Click += (s, e) => RefreshGrid();
            top.Controls.Add(refreshBtn);
            var todayBtn = new Button { Text = "Сегодня" };
            todayBtn.Click += (s, e) => { dateBox.Text = DateTime.Now.ToString("yyyy-MM-dd"); RefreshGrid(); };
            top.Controls.Add(todayBtn);
            Controls.Add(top);

            grid = new DataGridView { Dock = DockStyle.Fill, AllowUserToAddRows = false, ReadOnly = true, AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill };
            grid.Columns.Add("Id", "ID");
            grid.Columns.Add("MealType", "Приём");
            grid.Columns.Add("FoodName", "Продукт");
            grid.Columns.Add("Portion", "Порция");
            grid.Columns.Add("Calories", "Ккал");
            Controls.Add(grid);

            var bottom = new FlowLayoutPanel { Dock = DockStyle.Bottom, Padding = new Padding(5) };
            var addBtn = new Button { Text = "Добавить приём" };
            addBtn.Click += (s, e) => AddMeal();
            bottom.Controls.Add(addBtn);
            var deleteBtn = new Button { Text = "🗑 Удалить" };
            deleteBtn.Click += (s, e) => DeleteMeal();
            bottom.Controls.Add(deleteBtn);
            var statsBtn = new Button { Text = "📊 Статистика" };
            statsBtn.Click += (s, e) => ShowStats();
            bottom.Controls.Add(statsBtn);
            var randomBtn = new Button { Text = "🎲 Случайное меню" };
            randomBtn.Click += (s, e) => RandomMenu();
            bottom.Controls.Add(randomBtn);
            var exportBtn = new Button { Text = "💾 Экспорт CSV" };
            exportBtn.Click += (s, e) => ExportCSV();
            bottom.Controls.Add(exportBtn);
            Controls.Add(bottom);

            RefreshGrid();
        }

        private void RefreshGrid()
        {
            grid.Rows.Clear();
            string d = dateBox.Text;
            var items = Planner.GetPlanForDate(data, d);
            var stats = Planner.GetDayStats(data, d);
            foreach (var p in items)
            {
                var food = Planner.GetFood(data, p.FoodName);
                double cal = food == null ? 0 : food.Calories * p.Portion;
                grid.Rows.Add(p.Id, p.MealType, p.FoodName, p.Portion, cal);
            }
            Text = $"🍽️ Планировщик питания - {d} (Итого: {stats.totalCal:F1} ккал)";
        }

        private void AddMeal()
        {
            var dialog = new Form { Text = "Добавить приём", Size = new System.Drawing.Size(400, 250), StartPosition = FormStartPosition.CenterParent };
            var layout = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, Padding = new Padding(10) };
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            layout.Controls.Add(new Label { Text = "Продукт:", AutoSize = true }, 0, 0);
            foodBox = new TextBox();
            layout.Controls.Add(foodBox, 1, 0);
            layout.Controls.Add(new Label { Text = "Порция:", AutoSize = true }, 0, 1);
            portionBox = new TextBox { Text = "1.0" };
            layout.Controls.Add(portionBox, 1, 1);
            layout.Controls.Add(new Label { Text = "Тип приёма:", AutoSize = true }, 0, 2);
            mealTypeBox = new ComboBox { DropDownStyle = ComboBoxStyle.DropDownList, Items = { "breakfast", "lunch", "dinner", "snack" }, SelectedIndex = 1 };
            layout.Controls.Add(mealTypeBox, 1, 2);
            layout.Controls.Add(new Label { Text = "Заметки:", AutoSize = true }, 0, 3);
            notesBox = new TextBox();
            layout.Controls.Add(notesBox, 1, 3);
            var saveBtn = new Button { Text = "Сохранить", Dock = DockStyle.Right };
            saveBtn.Click += (s, e) =>
            {
                string fname = foodBox.Text.Trim();
                if (string.IsNullOrEmpty(fname)) { MessageBox.Show("Введите продукт"); return; }
                if (Planner.GetFood(data, fname) == null) { MessageBox.Show("Продукт не найден"); return; }
                double portion = double.Parse(portionBox.Text);
                string date = dateBox.Text;
                string mealType = mealTypeBox.SelectedItem.ToString();
                string notes = notesBox.Text;
                Planner.AddMealPlan(data, fname, portion, date, mealType, notes);
                dialog.Close();
                RefreshGrid();
            };
            layout.Controls.Add(saveBtn, 1, 4);
            dialog.Controls.Add(layout);
            dialog.ShowDialog();
        }

        private void DeleteMeal()
        {
            if (grid.SelectedRows.Count == 0) { MessageBox.Show("Выберите запись"); return; }
            int id = (int)grid.SelectedRows[0].Cells[0].Value;
            if (MessageBox.Show("Удалить пункт?", "Подтверждение", MessageBoxButtons.YesNo) == DialogResult.Yes)
            {
                Planner.DeletePlanItem(data, id);
                RefreshGrid();
            }
        }

        private void ShowStats()
        {
            string d = dateBox.Text;
            var stats = Planner.GetDayStats(data, d);
            string msg = $"📊 Статистика за {d}\n";
            msg += $"Калории: {stats.totalCal:F1}\n";
            msg += $"Белки: {stats.totalProt:F1} г\n";
            msg += $"Жиры: {stats.totalFat:F1} г\n";
            msg += $"Углеводы: {stats.totalCarbs:F1} г\n";
            if (stats.details.Any())
            {
                msg += "\nДетали:\n";
                foreach (var det in stats.details)
                    msg += $"  {det.mealType}: {det.food} x{det.portion:F1} = {det.calories:F1} ккал\n";
            }
            MessageBox.Show(msg);
        }

        private void RandomMenu()
        {
            string d = dateBox.Text;
            string limitStr = Microsoft.VisualBasic.Interaction.InputBox("Лимит калорий:", "", "2000");
            double limit = string.IsNullOrEmpty(limitStr) ? 2000 : double.Parse(limitStr);
            Planner.GenerateRandomMenu(data, d, limit);
            RefreshGrid();
            MessageBox.Show("Сгенерировано случайное меню");
        }

        private void ExportCSV()
        {
            var sfd = new SaveFileDialog { Filter = "CSV files|*.csv", DefaultExt = "csv" };
            if (sfd.ShowDialog() == DialogResult.OK)
            {
                Planner.ExportCSV(data, sfd.FileName, dateBox.Text);
                MessageBox.Show("Экспортировано");
            }
        }
    }
}
