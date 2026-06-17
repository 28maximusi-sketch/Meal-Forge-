// meal_planner.rs - Планировщик питания на Rust (CLI)
use serde::{Serialize, Deserialize};
use std::collections::HashMap;
use std::fs;
use std::io::{self, Write, BufRead};
use std::path::Path;
use std::str::FromStr;
use chrono::Local;
use rand::{seq::SliceRandom, thread_rng};

#[derive(Serialize, Deserialize, Clone)]
struct Food {
    name: String,
    calories: f64,
    protein: f64,
    fat: f64,
    carbs: f64,
    description: String,
}

#[derive(Serialize, Deserialize, Clone)]
struct MealPlanItem {
    id: u32,
    food_name: String,
    portion: f64,
    date: String,
    meal_type: String,
    notes: String,
}

#[derive(Serialize, Deserialize)]
struct Data {
    foods: Vec<Food>,
    plans: Vec<MealPlanItem>,
    next_id: u32,
}

impl Data {
    fn load() -> Self {
        let path = "meal_data.json";
        if Path::new(path).exists() {
            if let Ok(json) = fs::read_to_string(path) {
                if let Ok(data) = serde_json::from_str(&json) {
                    return data;
                }
            }
        }
        Data { foods: vec![], plans: vec![], next_id: 1 }
    }

    fn save(&self) {
        let json = serde_json::to_string_pretty(self).unwrap();
        fs::write("meal_data.json", json).unwrap();
    }
}

fn add_food(data: &mut Data, name: &str, calories: f64, protein: f64, fat: f64, carbs: f64, description: &str) -> Food {
    let f = Food {
        name: name.to_string(),
        calories,
        protein,
        fat,
        carbs,
        description: description.to_string(),
    };
    data.foods.push(f.clone());
    data.save();
    f
}

fn get_food(data: &Data, name: &str) -> Option<&Food> {
    data.foods.iter().find(|f| f.name.eq_ignore_ascii_case(name))
}

fn suggest_foods(data: &Data, prefix: &str) -> Vec<String> {
    data.foods.iter()
        .filter(|f| f.name.to_lowercase().starts_with(&prefix.to_lowercase()))
        .map(|f| f.name.clone())
        .collect()
}

fn add_meal_plan(data: &mut Data, food_name: &str, portion: f64, date: &str, meal_type: &str, notes: &str) -> MealPlanItem {
    let date = if date.is_empty() { Local::now().format("%Y-%m-%d").to_string() } else { date.to_string() };
    let item = MealPlanItem {
        id: data.next_id,
        food_name: food_name.to_string(),
        portion,
        date,
        meal_type: meal_type.to_string(),
        notes: notes.to_string(),
    };
    data.plans.push(item.clone());
    data.next_id += 1;
    data.save();
    item
}

fn delete_plan_item(data: &mut Data, id: u32) -> bool {
    let len = data.plans.len();
    data.plans.retain(|p| p.id != id);
    if data.plans.len() < len {
        data.save();
        return true;
    }
    false
}

fn get_plan_for_date(data: &Data, date_str: &str) -> Vec<MealPlanItem> {
    let mut items: Vec<_> = data.plans.iter().filter(|p| p.date == date_str).cloned().collect();
    items.sort_by(|a, b| a.meal_type.cmp(&b.meal_type));
    items
}

fn get_day_stats(data: &Data, date_str: &str) -> (f64, f64, f64, f64, Vec<HashMap<String, String>>) {
    let items = get_plan_for_date(data, date_str);
    let mut total_cal = 0.0;
    let mut total_protein = 0.0;
    let mut total_fat = 0.0;
    let mut total_carbs = 0.0;
    let mut details = Vec::new();
    for item in items {
        if let Some(food) = get_food(data, &item.food_name) {
            let factor = item.portion;
            total_cal += food.calories * factor;
            total_protein += food.protein * factor;
            total_fat += food.fat * factor;
            total_carbs += food.carbs * factor;
            let mut map = HashMap::new();
            map.insert("meal_type".to_string(), item.meal_type.clone());
            map.insert("food".to_string(), food.name.clone());
            map.insert("portion".to_string(), format!("{:.1}", item.portion));
            map.insert("calories".to_string(), format!("{:.1}", food.calories * factor));
            details.push(map);
        } else {
            let mut map = HashMap::new();
            map.insert("meal_type".to_string(), item.meal_type);
            map.insert("food".to_string(), item.food_name);
            map.insert("portion".to_string(), format!("{:.1}", item.portion));
            map.insert("calories".to_string(), "0.0".to_string());
            map.insert("warning".to_string(), "Неизвестный продукт".to_string());
            details.push(map);
        }
    }
    (total_cal, total_protein, total_fat, total_carbs, details)
}

fn generate_random_menu(data: &mut Data, date_str: &str, calories_limit: f64) -> Vec<MealPlanItem> {
    if data.foods.is_empty() {
        return vec![];
    }
    let mut rng = thread_rng();
    let mut available = data.foods.clone();
    available.shuffle(&mut rng);
    let mut total_cal = 0.0;
    let mut selected = Vec::new();
    let meal_types = vec!["breakfast", "lunch", "dinner", "snack"];
    let mut idx = 0;
    for food in available {
        let portion_choices = [0.5, 0.75, 1.0, 1.25, 1.5];
        let portion = *portion_choices.choose(&mut rng).unwrap();
        let cal = food.calories * portion;
        if total_cal + cal <= calories_limit {
            selected.push((food, portion));
            total_cal += cal;
        }
        if total_cal >= calories_limit * 0.8 {
            break;
        }
    }
    let mut items = Vec::new();
    for (food, portion) in selected {
        let mtype = meal_types[idx % meal_types.len()];
        idx += 1;
        let item = MealPlanItem {
            id: data.next_id,
            food_name: food.name.clone(),
            portion,
            date: date_str.to_string(),
            meal_type: mtype.to_string(),
            notes: "Сгенерировано автоматически".to_string(),
        };
        data.plans.push(item.clone());
        data.next_id += 1;
        items.push(item);
    }
    data.save();
    items
}

fn export_csv(data: &Data, filepath: &str, date_str: Option<&str>) -> Result<(), Box<dyn std::error::Error>> {
    let mut writer = csv::Writer::from_path(filepath)?;
    writer.write_record(&["Date", "Meal Type", "Food", "Portion", "Calories", "Protein", "Fat", "Carbs"])?;
    let plans = if let Some(d) = date_str {
        get_plan_for_date(data, d)
    } else {
        data.plans.clone()
    };
    for p in plans {
        let food = get_food(data, &p.food_name);
        let (c, pr, fa, ca) = if let Some(f) = food {
            (f.calories * p.portion, f.protein * p.portion, f.fat * p.portion, f.carbs * p.portion)
        } else {
            (0.0, 0.0, 0.0, 0.0)
        };
        writer.serialize((&p.date, &p.meal_type, &p.food_name, p.portion, c, pr, fa, ca))?;
    }
    writer.flush()?;
    Ok(())
}

fn import_csv(data: &mut Data, filepath: &str) -> Result<(), Box<dyn std::error::Error>> {
    let mut reader = csv::Reader::from_path(filepath)?;
    for result in reader.records() {
        let record = result?;
        if record.len() < 8 { continue; }
        let date = record[0].to_string();
        let meal_type = record[1].to_string();
        let food_name = record[2].to_string();
        let portion = record[3].parse::<f64>().unwrap_or(1.0);
        add_meal_plan(data, &food_name, portion, &date, &meal_type, "Импортировано из CSV");
    }
    Ok(())
}

fn read_line(prompt: &str) -> String {
    print!("{}", prompt);
    io::stdout().flush().unwrap();
    let mut input = String::new();
    io::stdin().read_line(&mut input).unwrap();
    input.trim().to_string()
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 2 {
        interactive_mode();
        return;
    }
    let mut data = Data::load();
    match args[1].as_str() {
        "add-food" => {
            let mut name = String::new();
            let mut calories = 0.0;
            let mut protein = 0.0;
            let mut fat = 0.0;
            let mut carbs = 0.0;
            let mut description = String::new();
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--name" => { name = args[i+1].clone(); i += 2; }
                    "--calories" => { calories = args[i+1].parse().unwrap_or(0.0); i += 2; }
                    "--protein" => { protein = args[i+1].parse().unwrap_or(0.0); i += 2; }
                    "--fat" => { fat = args[i+1].parse().unwrap_or(0.0); i += 2; }
                    "--carbs" => { carbs = args[i+1].parse().unwrap_or(0.0); i += 2; }
                    "--description" => { description = args[i+1].clone(); i += 2; }
                    _ => { i += 1; }
                }
            }
            if name.is_empty() || calories == 0.0 {
                println!("Укажите --name и --calories");
                return;
            }
            let f = add_food(&mut data, &name, calories, protein, fat, carbs, &description);
            println!("✅ Продукт '{}' добавлен", f.name);
        }
        "add-meal" => {
            let mut food_name = String::new();
            let mut portion = 1.0;
            let mut date = String::new();
            let mut meal_type = "lunch".to_string();
            let mut notes = String::new();
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--food" => { food_name = args[i+1].clone(); i += 2; }
                    "--portion" => { portion = args[i+1].parse().unwrap_or(1.0); i += 2; }
                    "--date" => { date = args[i+1].clone(); i += 2; }
                    "--meal-type" => { meal_type = args[i+1].clone(); i += 2; }
                    "--notes" => { notes = args[i+1].clone(); i += 2; }
                    _ => { i += 1; }
                }
            }
            if food_name.is_empty() {
                println!("Укажите --food");
                return;
            }
            if get_food(&data, &food_name).is_none() {
                println!("Продукт не найден. Сначала добавьте его.");
                return;
            }
            let item = add_meal_plan(&mut data, &food_name, portion, &date, &meal_type, &notes);
            println!("✅ Приём пищи #{} добавлен", item.id);
        }
        "list" => {
            let mut date = String::new();
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--date" {
                    date = args[i+1].clone();
                    i += 2;
                } else { i += 1; }
            }
            if date.is_empty() {
                date = Local::now().format("%Y-%m-%d").to_string();
            }
            let items = get_plan_for_date(&data, &date);
            if items.is_empty() {
                println!("Нет записей на {}", date);
            } else {
                let (total_cal, total_prot, total_fat, total_carbs, _) = get_day_stats(&data, &date);
                println!("📅 План на {}", date);
                println!("{:<4} {:<10} {:<20} {:<8} {:<8}", "ID", "Приём", "Продукт", "Порция", "Ккал");
                for p in items {
                    let food = get_food(&data, &p.food_name);
                    let cal = if let Some(f) = food { f.calories * p.portion } else { 0.0 };
                    println!("{:<4} {:<10} {:<20} {:<8.1} {:<8.1}", p.id, p.meal_type, p.food_name, p.portion, cal);
                }
                println!("Итого: {:.1} ккал, Б: {:.1}, Ж: {:.1}, У: {:.1}", total_cal, total_prot, total_fat, total_carbs);
            }
        }
        "stats" => {
            let mut date = String::new();
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--date" {
                    date = args[i+1].clone();
                    i += 2;
                } else { i += 1; }
            }
            if date.is_empty() {
                date = Local::now().format("%Y-%m-%d").to_string();
            }
            let (total_cal, total_prot, total_fat, total_carbs, details) = get_day_stats(&data, &date);
            println!("📊 Статистика за {}", date);
            println!("Калории: {:.1}", total_cal);
            println!("Белки: {:.1} г", total_prot);
            println!("Жиры: {:.1} г", total_fat);
            println!("Углеводы: {:.1} г", total_carbs);
            if !details.is_empty() {
                println!("Детали:");
                for d in details {
                    println!("  {}: {} x{} = {} ккал", d.get("meal_type").unwrap(), d.get("food").unwrap(), d.get("portion").unwrap(), d.get("calories").unwrap());
                }
            }
        }
        "delete" => {
            let mut id = 0;
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--id" {
                    id = args[i+1].parse().unwrap_or(0);
                    i += 2;
                } else { i += 1; }
            }
            if id == 0 {
                println!("Укажите --id");
                return;
            }
            if delete_plan_item(&mut data, id) {
                println!("✅ Пункт #{} удалён", id);
            } else {
                println!("❌ Пункт #{} не найден", id);
            }
        }
        "random" => {
            let mut date = String::new();
            let mut limit = 2000.0;
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--date" => { date = args[i+1].clone(); i += 2; }
                    "--calories-limit" => { limit = args[i+1].parse().unwrap_or(2000.0); i += 2; }
                    _ => { i += 1; }
                }
            }
            if date.is_empty() {
                date = Local::now().format("%Y-%m-%d").to_string();
            }
            let items = generate_random_menu(&mut data, &date, limit);
            println!("✅ Сгенерировано {} блюд на {}", items.len(), date);
        }
        "export" => {
            let mut output = String::new();
            let mut date = None;
            let mut i = 2;
            while i < args.len() {
                match args[i].as_str() {
                    "--output" => { output = args[i+1].clone(); i += 2; }
                    "--date" => { date = Some(args[i+1].clone()); i += 2; }
                    _ => { i += 1; }
                }
            }
            if output.is_empty() {
                println!("Укажите --output");
                return;
            }
            if let Err(e) = export_csv(&data, &output, date.as_deref()) {
                println!("Ошибка экспорта: {}", e);
            } else {
                println!("Экспортировано в {}", output);
            }
        }
        "import" => {
            let mut file = String::new();
            let mut i = 2;
            while i < args.len() {
                if args[i] == "--file" {
                    file = args[i+1].clone();
                    i += 2;
                } else { i += 1; }
            }
            if file.is_empty() {
                println!("Укажите --file");
                return;
            }
            if let Err(e) = import_csv(&mut data, &file) {
                println!("Ошибка импорта: {}", e);
            } else {
                println!("Импортировано из {}", file);
            }
        }
        _ => interactive_mode(),
    }
}

fn interactive_mode() {
    let mut data = Data::load();
    let stdin = io::stdin();
    let mut stdout = io::stdout();
    loop {
        println!("\n🍽️ Планировщик питания (интерактивный)");
        println!("1. Добавить продукт");
        println!("2. Добавить приём пищи");
        println!("3. Показать план на день");
        println!("4. Статистика за день");
        println!("5. Удалить пункт плана");
        println!("6. Сгенерировать случайное меню");
        println!("7. Экспорт CSV");
        println!("8. Импорт CSV");
        println!("0. Выход");
        print!("Выберите действие: ");
        stdout.flush().unwrap();
        let mut choice = String::new();
        stdin.read_line(&mut choice).unwrap();
        match choice.trim() {
            "0" => break,
            "1" => {
                let name = read_line("Название продукта: ");
                if name.is_empty() { println!("Название обязательно"); continue; }
                let cal = read_line("Калории: ").parse::<f64>().unwrap_or(0.0);
                let prot = read_line("Белки (г): ").parse::<f64>().unwrap_or(0.0);
                let fat = read_line("Жиры (г): ").parse::<f64>().unwrap_or(0.0);
                let carbs = read_line("Углеводы (г): ").parse::<f64>().unwrap_or(0.0);
                let desc = read_line("Описание (необязательно): ");
                add_food(&mut data, &name, cal, prot, fat, carbs, &desc);
                println!("✅ Продукт '{}' добавлен", name);
            }
            "2" => {
                let food_name = read_line("Название продукта: ");
                if food_name.is_empty() { println!("Введите название"); continue; }
                if get_food(&data, &food_name).is_none() {
                    println!("Продукт не найден. Сначала добавьте его.");
                    continue;
                }
                let portion = read_line("Количество порций (по умолчанию 1): ").parse::<f64>().unwrap_or(1.0);
                let date = read_line("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                let date = if date.is_empty() { Local::now().format("%Y-%m-%d").to_string() } else { date };
                let meal_type = read_line("Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ");
                let meal_type = if meal_type.is_empty() { "lunch".to_string() } else { meal_type };
                let notes = read_line("Заметки: ");
                let item = add_meal_plan(&mut data, &food_name, portion, &date, &meal_type, &notes);
                println!("✅ Добавлен приём пищи #{}", item.id);
            }
            "3" => {
                let date = read_line("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                let date = if date.is_empty() { Local::now().format("%Y-%m-%d").to_string() } else { date };
                let items = get_plan_for_date(&data, &date);
                if items.is_empty() {
                    println!("Нет записей на {}", date);
                } else {
                    let (total_cal, total_prot, total_fat, total_carbs, _) = get_day_stats(&data, &date);
                    println!("📅 План на {}", date);
                    println!("{:<4} {:<10} {:<20} {:<8} {:<8}", "ID", "Приём", "Продукт", "Порция", "Ккал");
                    for p in items {
                        let food = get_food(&data, &p.food_name);
                        let cal = if let Some(f) = food { f.calories * p.portion } else { 0.0 };
                        println!("{:<4} {:<10} {:<20} {:<8.1} {:<8.1}", p.id, p.meal_type, p.food_name, p.portion, cal);
                    }
                    println!("Итого: {:.1} ккал, Б: {:.1}, Ж: {:.1}, У: {:.1}", total_cal, total_prot, total_fat, total_carbs);
                }
            }
            "4" => {
                let date = read_line("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                let date = if date.is_empty() { Local::now().format("%Y-%m-%d").to_string() } else { date };
                let (total_cal, total_prot, total_fat, total_carbs, details) = get_day_stats(&data, &date);
                println!("📊 Статистика за {}", date);
                println!("Калории: {:.1}", total_cal);
                println!("Белки: {:.1} г", total_prot);
                println!("Жиры: {:.1} г", total_fat);
                println!("Углеводы: {:.1} г", total_carbs);
                if !details.is_empty() {
                    println!("Детали:");
                    for d in details {
                        println!("  {}: {} x{} = {} ккал", d.get("meal_type").unwrap(), d.get("food").unwrap(), d.get("portion").unwrap(), d.get("calories").unwrap());
                    }
                }
            }
            "5" => {
                let id_str = read_line("ID пункта для удаления: ");
                let id = id_str.parse::<u32>().unwrap_or(0);
                if id == 0 { println!("Неверный ID"); continue; }
                if delete_plan_item(&mut data, id) {
                    println!("✅ Удалено");
                } else {
                    println!("❌ Не найдено");
                }
            }
            "6" => {
                let date = read_line("Дата (ГГГГ-ММ-ДД, Enter сегодня): ");
                let date = if date.is_empty() { Local::now().format("%Y-%m-%d").to_string() } else { date };
                let limit = read_line("Лимит калорий (по умолчанию 2000): ").parse::<f64>().unwrap_or(2000.0);
                let items = generate_random_menu(&mut data, &date, limit);
                println!("✅ Сгенерировано {} блюд", items.len());
            }
            "7" => {
                let file = read_line("Имя файла (CSV): ");
                let file = if file.is_empty() { "meal_plan.csv".to_string() } else { file };
                let date = read_line("Дата (оставьте пустым для всех): ");
                let date = if date.is_empty() { None } else { Some(date) };
                if let Err(e) = export_csv(&data, &file, date.as_deref()) {
                    println!("Ошибка экспорта: {}", e);
                } else {
                    println!("Экспортировано в {}", file);
                }
            }
            "8" => {
                let file = read_line("Имя файла (CSV): ");
                if file.is_empty() { println!("Укажите файл"); continue; }
                if let Err(e) = import_csv(&mut data, &file) {
                    println!("Ошибка импорта: {}", e);
                } else {
                    println!("Импортировано из {}", file);
                }
            }
            _ => println!("Неверный выбор"),
        }
    }
}
