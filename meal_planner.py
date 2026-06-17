#!/usr/bin/env python3
"""
meal_planner.py - Планировщик питания на Python (CLI + Tkinter GUI)
Поддерживает: базу продуктов, планирование на день, подсчёт калорий, автодополнение.
"""
import argparse
import sys
import json
import os
from datetime import datetime, date
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, asdict, field
from collections import defaultdict

try:
    import tkinter as tk
    from tkinter import ttk, filedialog, messagebox, scrolledtext, Listbox, END, SINGLE
    GUI_AVAILABLE = True
except ImportError:
    GUI_AVAILABLE = False

DATA_FILE = "meal_data.json"

@dataclass
class Food:
    """Продукт или блюдо"""
    name: str
    calories: float
    protein: float  # граммы
    fat: float
    carbs: float
    description: str = ""

@dataclass
class MealPlanItem:
    """Пункт плана (приём пищи)"""
    id: int
    food_name: str
    portion: float          # количество порций
    date: str               # YYYY-MM-DD
    meal_type: str          # breakfast, lunch, dinner, snack
    notes: str = ""

class MealPlanner:
    def __init__(self, data_file=DATA_FILE):
        self.data_file = data_file
        self.foods: List[Food] = []
        self.plans: List[MealPlanItem] = []
        self.next_id = 1
        self.load()

    def load(self):
        if os.path.exists(self.data_file):
            try:
                with open(self.data_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.foods = [Food(**f) for f in data.get('foods', [])]
                    self.plans = [MealPlanItem(**p) for p in data.get('plans', [])]
                    self.next_id = max([p.id for p in self.plans], default=0) + 1
            except:
                self.foods = []
                self.plans = []
                self.next_id = 1
        else:
            self.foods = []
            self.plans = []
            self.next_id = 1

    def save(self):
        with open(self.data_file, 'w', encoding='utf-8') as f:
            json.dump({
                'foods': [asdict(f) for f in self.foods],
                'plans': [asdict(p) for p in self.plans]
            }, f, indent=2, ensure_ascii=False)

    def add_food(self, name: str, calories: float, protein: float, fat: float, carbs: float, description: str = "") -> Food:
        food = Food(name, calories, protein, fat, carbs, description)
        self.foods.append(food)
        self.save()
        return food

    def get_food(self, name: str) -> Optional[Food]:
        for f in self.foods:
            if f.name.lower() == name.lower():
                return f
        return None

    def suggest_foods(self, prefix: str) -> List[str]:
        return [f.name for f in self.foods if f.name.lower().startswith(prefix.lower())]

    def add_meal_plan(self, food_name: str, portion: float, date_str: str, meal_type: str, notes: str = "") -> MealPlanItem:
        if not date_str:
            date_str = date.today().isoformat()
        item = MealPlanItem(self.next_id, food_name, portion, date_str, meal_type, notes)
        self.plans.append(item)
        self.next_id += 1
        self.save()
        return item

    def delete_plan_item(self, plan_id: int) -> bool:
        for i, p in enumerate(self.plans):
            if p.id == plan_id:
                del self.plans[i]
                self.save()
                return True
        return False

    def get_plan_for_date(self, date_str: str) -> List[MealPlanItem]:
        return sorted([p for p in self.plans if p.date == date_str], key=lambda x: x.meal_type)

    def get_day_stats(self, date_str: str) -> Dict:
        items = self.get_plan_for_date(date_str)
        total_cal = 0.0
        total_protein = 0.0
        total_fat = 0.0
        total_carbs = 0.0
        details = []
        for item in items:
            food = self.get_food(item.food_name)
            if food:
                factor = item.portion
                details.append({
                    'food': food.name,
                    'portion': item.portion,
                    'calories': food.calories * factor,
                    'protein': food.protein * factor,
                    'fat': food.fat * factor,
                    'carbs': food.carbs * factor,
                    'meal_type': item.meal_type
                })
                total_cal += food.calories * factor
                total_protein += food.protein * factor
                total_fat += food.fat * factor
                total_carbs += food.carbs * factor
            else:
                details.append({
                    'food': item.food_name,
                    'portion': item.portion,
                    'calories': 0,
                    'protein': 0,
                    'fat': 0,
                    'carbs': 0,
                    'meal_type': item.meal_type,
                    'warning': 'Неизвестный продукт'
                })
        return {
            'total_calories': total_cal,
            'total_protein': total_protein,
            'total_fat': total_fat,
            'total_carbs': total_carbs,
            'details': details
        }

    def generate_random_menu(self, date_str: str, calories_limit: float) -> List[MealPlanItem]:
        """Создаёт случайное меню на день из доступных продуктов"""
        import random
        if not self.foods:
            return []
        # Выбираем случайные продукты, пока не достигнем лимита
        selected = []
        total_cal = 0.0
        # Сортируем по калорийности, чтобы разнообразить
        available = self.foods.copy()
        random.shuffle(available)
        for food in available:
            # случайное количество порций от 0.5 до 2
            portion = random.choice([0.5, 0.75, 1.0, 1.25, 1.5])
            cal = food.calories * portion
            if total_cal + cal <= calories_limit:
                selected.append((food, portion))
                total_cal += cal
            if total_cal >= calories_limit * 0.8:
                break
        # Преобразуем в MealPlanItem
        items = []
        meal_types = ['breakfast', 'lunch', 'dinner', 'snack']
        for idx, (food, portion) in enumerate(selected):
            mtype = meal_types[idx % len(meal_types)]
            item = MealPlanItem(self.next_id, food.name, portion, date_str, mtype, 'Сгенерировано автоматически')
            self.plans.append(item)
            self.next_id += 1
            items.append(item)
        self.save()
        return items

    def export_csv(self, filepath: str, date_str: str = None):
        import csv
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(["Date", "Meal Type", "Food", "Portion", "Calories", "Protein", "Fat", "Carbs"])
            plans = self.plans if date_str is None else self.get_plan_for_date(date_str)
            for p in plans:
                food = self.get_food(p.food_name)
                c = p.portion * food.calories if food else 0
                pr = p.portion * food.protein if food else 0
                fa = p.portion * food.fat if food else 0
                ca = p.portion * food.carbs if food else 0
                writer.writerow([p.date, p.meal_type, p.food_name, p.portion, c, pr, fa, ca])

    def import_csv(self, filepath: str):
        import csv
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                date_str = row['Date']
                meal_type = row['Meal Type']
                food_name = row['Food']
                portion = float(row['Portion'])
                notes = "Импортировано из CSV"
                self.add_meal_plan(food_name, portion, date_str, meal_type, notes)

# ========== CLI ==========
def cli():
    parser = argparse.ArgumentParser(description="Планировщик питания")
    subparsers = parser.add_subparsers(dest="command", help="Команды")

    # add-food
    food_parser = subparsers.add_parser("add-food", help="Добавить продукт")
    food_parser.add_argument("name", help="Название")
    food_parser.add_argument("--calories", type=float, required=True, help="Калории")
    food_parser.add_argument("--protein", type=float, default=0, help="Белки (г)")
    food_parser.add_argument("--fat", type=float, default=0, help="Жиры (г)")
    food_parser.add_argument("--carbs", type=float, default=0, help="Углеводы (г)")
    food_parser.add_argument("--description", default="", help="Описание")

    # add-meal
    meal_parser = subparsers.add_parser("add-meal", help="Добавить приём пищи")
    meal_parser.add_argument("--food", required=True, help="Название продукта")
    meal_parser.add_argument("--portion", type=float, default=1.0, help="Количество порций")
    meal_parser.add_argument("--date", help="Дата (ГГГГ-ММ-ДД)")
    meal_parser.add_argument("--meal-type", choices=['breakfast','lunch','dinner','snack'], default='lunch', help="Тип приёма")
    meal_parser.add_argument("--notes", default="", help="Заметки")

    # list
    list_parser = subparsers.add_parser("list", help="Показать план на день")
    list_parser.add_argument("--date", help="Дата (по умолчанию сегодня)")

    # stats
    stats_parser = subparsers.add_parser("stats", help="Статистика за день")
    stats_parser.add_argument("--date", help="Дата")

    # delete
    delete_parser = subparsers.add_parser("delete", help="Удалить пункт плана")
    delete_parser.add_argument("--id", type=int, required=True, help="ID записи")

    # random
    random_parser = subparsers.add_parser("random", help="Сгенерировать случайное меню")
    random_parser.add_argument("--calories-limit", type=float, default=2000, help="Лимит калорий")
    random_parser.add_argument("--date", help="Дата")

    # export
    export_parser = subparsers.add_parser("export", help="Экспорт в CSV")
    export_parser.add_argument("--output", "-o", required=True, help="Имя файла")
    export_parser.add_argument("--date", help="Дата (если не указана, то все)")

    # import
    import_parser = subparsers.add_parser("import", help="Импорт из CSV")
    import_parser.add_argument("--file", required=True, help="CSV файл")

    # interactive mode
    parser.add_argument("--gui", action="store_true", help="Запустить GUI")
    args = parser.parse_args()

    if args.gui and GUI_AVAILABLE:
        root = tk.Tk()
        app = MealPlannerGUI(root)
        root.mainloop()
        return

    planner = MealPlanner()
    if args.command == "add-food":
        f = planner.add_food(args.name, args.calories, args.protein, args.fat, args.carbs, args.description)
        print(f"✅ Продукт '{f.name}' добавлен")
    elif args.command == "add-meal":
        item = planner.add_meal_plan(args.food, args.portion, args.date or date.today().isoformat(), args.meal_type, args.notes)
        print(f"✅ Приём пищи #{item.id} добавлен")
    elif args.command == "list":
        d = args.date or date.today().isoformat()
        items = planner.get_plan_for_date(d)
        if not items:
            print(f"Нет записей на {d}")
        else:
            stats = planner.get_day_stats(d)
            print(f"📅 План на {d}")
            print(f"{'ID':<4} {'Приём':<10} {'Продукт':<20} {'Порция':<8} {'Ккал':<8}")
            for p in items:
                food = planner.get_food(p.food_name)
                cal = food.calories * p.portion if food else 0
                print(f"{p.id:<4} {p.meal_type:<10} {p.food_name:<20} {p.portion:<8.1f} {cal:<8.1f}")
            print(f"Итого: {stats['total_calories']:.1f} ккал, Б: {stats['total_protein']:.1f}, Ж: {stats['total_fat']:.1f}, У: {stats['total_carbs']:.1f}")
    elif args.command == "stats":
        d = args.date or date.today().isoformat()
        stats = planner.get_day_stats(d)
        print(f"📊 Статистика за {d}")
        print(f"Калории: {stats['total_calories']:.1f}")
        print(f"Белки: {stats['total_protein']:.1f} г")
        print(f"Жиры: {stats['total_fat']:.1f} г")
        print(f"Углеводы: {stats['total_carbs']:.1f} г")
        if stats['details']:
            print("Детали:")
            for d in stats['details']:
                print(f"  {d['meal_type']}: {d['food']} x{d['portion']:.1f} = {d['calories']:.1f} ккал")
    elif args.command == "delete":
        if planner.delete_plan_item(args.id):
            print(f"✅ Пункт #{args.id} удалён")
        else:
            print(f"❌ Пункт #{args.id} не найден")
    elif args.command == "random":
        d = args.date or date.today().isoformat()
        items = planner.generate_random_menu(d, args.calories_limit)
        print(f"✅ Сгенерировано {len(items)} блюд на {d}")
    elif args.command == "export":
        planner.export_csv(args.output, args.date)
        print(f"Экспортировано в {args.output}")
    elif args.command == "import":
        planner.import_csv(args.file)
        print(f"Импортировано из {args.file}")
    else:
        interactive_mode(planner)

def interactive_mode(planner):
    while True:
        print("\n🍽️ Планировщик питания (интерактивный)")
        print("1. Добавить продукт")
        print("2. Добавить приём пищи")
        print("3. Показать план на день")
        print("4. Статистика за день")
        print("5. Удалить пункт плана")
        print("6. Сгенерировать случайное меню")
        print("7. Экспорт CSV")
        print("8. Импорт CSV")
        print("0. Выход")
        choice = input("Выберите действие: ").strip()
        if choice == "0":
            break
        elif choice == "1":
            name = input("Название продукта: ").strip()
            if not name:
                print("Название обязательно")
                continue
            try:
                cal = float(input("Калории: ").strip())
                prot = float(input("Белки (г): ").strip() or "0")
                fat = float(input("Жиры (г): ").strip() or "0")
                carbs = float(input("Углеводы (г): ").strip() or "0")
                desc = input("Описание (необязательно): ").strip()
            except ValueError:
                print("Неверные числовые данные")
                continue
            planner.add_food(name, cal, prot, fat, carbs, desc)
            print(f"✅ Продукт '{name}' добавлен")
        elif choice == "2":
            food_name = input("Название продукта: ").strip()
            if not food_name:
                print("Введите название")
                continue
            # Проверяем, существует ли продукт
            if not planner.get_food(food_name):
                print("Продукт не найден в базе. Сначала добавьте его.")
                continue
            try:
                portion = float(input("Количество порций (по умолчанию 1): ").strip() or "1")
            except ValueError:
                portion = 1.0
            date_str = input("Дата (ГГГГ-ММ-ДД, Enter сегодня): ").strip()
            if not date_str:
                date_str = date.today().isoformat()
            meal_type = input("Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ").strip()
            if meal_type not in ['breakfast','lunch','dinner','snack']:
                meal_type = 'lunch'
            notes = input("Заметки: ").strip()
            item = planner.add_meal_plan(food_name, portion, date_str, meal_type, notes)
            print(f"✅ Добавлен приём пищи #{item.id}")
        elif choice == "3":
            d = input("Дата (ГГГГ-ММ-ДД, Enter сегодня): ").strip()
            if not d:
                d = date.today().isoformat()
            items = planner.get_plan_for_date(d)
            if not items:
                print(f"Нет записей на {d}")
            else:
                stats = planner.get_day_stats(d)
                print(f"📅 План на {d}")
                print(f"{'ID':<4} {'Приём':<10} {'Продукт':<20} {'Порция':<8} {'Ккал':<8}")
                for p in items:
                    food = planner.get_food(p.food_name)
                    cal = food.calories * p.portion if food else 0
                    print(f"{p.id:<4} {p.meal_type:<10} {p.food_name:<20} {p.portion:<8.1f} {cal:<8.1f}")
                print(f"Итого: {stats['total_calories']:.1f} ккал, Б: {stats['total_protein']:.1f}, Ж: {stats['total_fat']:.1f}, У: {stats['total_carbs']:.1f}")
        elif choice == "4":
            d = input("Дата (ГГГГ-ММ-ДД, Enter сегодня): ").strip()
            if not d:
                d = date.today().isoformat()
            stats = planner.get_day_stats(d)
            print(f"📊 Статистика за {d}")
            print(f"Калории: {stats['total_calories']:.1f}")
            print(f"Белки: {stats['total_protein']:.1f} г")
            print(f"Жиры: {stats['total_fat']:.1f} г")
            print(f"Углеводы: {stats['total_carbs']:.1f} г")
        elif choice == "5":
            try:
                pid = int(input("ID пункта для удаления: ").strip())
            except ValueError:
                print("Неверный ID")
                continue
            if planner.delete_plan_item(pid):
                print("✅ Удалено")
            else:
                print("❌ Не найдено")
        elif choice == "6":
            d = input("Дата (ГГГГ-ММ-ДД, Enter сегодня): ").strip()
            if not d:
                d = date.today().isoformat()
            try:
                limit = float(input("Лимит калорий (по умолчанию 2000): ").strip() or "2000")
            except ValueError:
                limit = 2000
            items = planner.generate_random_menu(d, limit)
            print(f"✅ Сгенерировано {len(items)} блюд")
        elif choice == "7":
            fname = input("Имя файла (CSV): ").strip()
            if not fname:
                fname = "meal_plan.csv"
            d = input("Дата (оставьте пустым для всех): ").strip()
            planner.export_csv(fname, d or None)
            print(f"Экспортировано в {fname}")
        elif choice == "8":
            fname = input("Имя файла (CSV): ").strip()
            if not fname:
                print("Укажите файл")
                continue
            planner.import_csv(fname)
            print(f"Импортировано из {fname}")
        else:
            print("Неверный выбор")

# ========== GUI ==========
if GUI_AVAILABLE:
    class MealPlannerGUI:
        def __init__(self, root):
            self.root = root
            self.root.title("🍽️ Планировщик питания")
            self.root.geometry("800x600")
            self.root.resizable(True, True)
            self.planner = MealPlanner()
            self.create_widgets()
            self.refresh_list()

        def create_widgets(self):
            main = ttk.Frame(self.root, padding="10")
            main.pack(fill=tk.BOTH, expand=True)

            # Дата
            date_frame = ttk.Frame(main)
            date_frame.pack(fill=tk.X, pady=5)
            ttk.Label(date_frame, text="Дата:").pack(side=tk.LEFT)
            self.date_var = tk.StringVar(value=date.today().isoformat())
            ttk.Entry(date_frame, textvariable=self.date_var, width=12).pack(side=tk.LEFT, padx=5)
            ttk.Button(date_frame, text="Обновить", command=self.refresh_list).pack(side=tk.LEFT, padx=5)
            ttk.Button(date_frame, text="Сегодня", command=self.set_today).pack(side=tk.LEFT, padx=5)

            # Таблица плана
            self.tree = ttk.Treeview(main, columns=("id", "meal_type", "food", "portion", "cal"), show="headings")
            self.tree.heading("id", text="ID")
            self.tree.heading("meal_type", text="Приём")
            self.tree.heading("food", text="Продукт")
            self.tree.heading("portion", text="Порция")
            self.tree.heading("cal", text="Ккал")
            self.tree.column("id", width=40)
            self.tree.column("meal_type", width=80)
            self.tree.column("food", width=200)
            self.tree.column("portion", width=60)
            self.tree.column("cal", width=80)
            scroll = ttk.Scrollbar(main, orient=tk.VERTICAL, command=self.tree.yview)
            self.tree.configure(yscrollcommand=scroll.set)
            self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
            scroll.pack(side=tk.RIGHT, fill=tk.Y)

            # Нижняя панель: кнопки
            bottom = ttk.Frame(main)
            bottom.pack(fill=tk.X, pady=5)
            ttk.Button(bottom, text="➕ Добавить приём", command=self.add_meal).pack(side=tk.LEFT, padx=5)
            ttk.Button(bottom, text="🗑 Удалить", command=self.delete_meal).pack(side=tk.LEFT, padx=5)
            ttk.Button(bottom, text="📊 Статистика", command=self.show_stats).pack(side=tk.LEFT, padx=5)
            ttk.Button(bottom, text="🎲 Случайное меню", command=self.random_menu).pack(side=tk.LEFT, padx=5)
            ttk.Button(bottom, text="💾 Экспорт CSV", command=self.export_csv).pack(side=tk.LEFT, padx=5)

            # Информация о калориях
            self.info_label = ttk.Label(main, text="", font=('Arial', 10))
            self.info_label.pack(pady=5)

        def set_today(self):
            self.date_var.set(date.today().isoformat())
            self.refresh_list()

        def refresh_list(self):
            self.tree.delete(*self.tree.get_children())
            d = self.date_var.get()
            items = self.planner.get_plan_for_date(d)
            stats = self.planner.get_day_stats(d)
            total_cal = stats['total_calories']
            for item in items:
                food = self.planner.get_food(item.food_name)
                cal = food.calories * item.portion if food else 0
                self.tree.insert("", tk.END, values=(item.id, item.meal_type, item.food_name, item.portion, cal))
            self.info_label.config(text=f"Итого: {total_cal:.1f} ккал, Б: {stats['total_protein']:.1f}, Ж: {stats['total_fat']:.1f}, У: {stats['total_carbs']:.1f}")

        def get_selected_id(self):
            selected = self.tree.selection()
            if not selected:
                messagebox.showwarning("Выберите запись")
                return None
            return int(self.tree.item(selected[0])['values'][0])

        def add_meal(self):
            dialog = tk.Toplevel(self.root)
            dialog.title("Добавить приём пищи")
            dialog.geometry("400x300")
            ttk.Label(dialog, text="Продукт:").grid(row=0, column=0, padx=5, pady=5, sticky="w")
            food_var = tk.StringVar()
            food_entry = ttk.Entry(dialog, textvariable=food_var, width=30)
            food_entry.grid(row=0, column=1, padx=5, pady=5)
            # автодополнение
            def on_food_change(*args):
                prefix = food_var.get().strip()
                if prefix:
                    suggestions = self.planner.suggest_foods(prefix)
                    # простая подсказка в виде списка (можно сделать более сложно)
            food_var.trace('w', on_food_change)

            ttk.Label(dialog, text="Порция:").grid(row=1, column=0, padx=5, pady=5, sticky="w")
            portion_var = tk.DoubleVar(value=1.0)
            ttk.Spinbox(dialog, from_=0.1, to=5.0, increment=0.1, textvariable=portion_var, width=10).grid(row=1, column=1, padx=5, pady=5, sticky="w")
            ttk.Label(dialog, text="Тип приёма:").grid(row=2, column=0, padx=5, pady=5, sticky="w")
            meal_type_var = tk.StringVar(value="lunch")
            ttk.Combobox(dialog, textvariable=meal_type_var, values=['breakfast','lunch','dinner','snack'], state="readonly").grid(row=2, column=1, padx=5, pady=5, sticky="w")
            ttk.Label(dialog, text="Заметки:").grid(row=3, column=0, padx=5, pady=5, sticky="w")
            notes_entry = ttk.Entry(dialog, width=30)
            notes_entry.grid(row=3, column=1, padx=5, pady=5)

            def save():
                food_name = food_var.get().strip()
                if not food_name:
                    messagebox.showerror("Ошибка", "Введите название продукта")
                    return
                if not self.planner.get_food(food_name):
                    messagebox.showerror("Ошибка", "Продукт не найден в базе")
                    return
                portion = portion_var.get()
                date_str = self.date_var.get()
                meal_type = meal_type_var.get()
                notes = notes_entry.get().strip()
                self.planner.add_meal_plan(food_name, portion, date_str, meal_type, notes)
                dialog.destroy()
                self.refresh_list()
            ttk.Button(dialog, text="Сохранить", command=save).grid(row=4, column=0, columnspan=2, pady=10)

        def delete_meal(self):
            pid = self.get_selected_id()
            if pid is not None:
                if messagebox.askyesno("Удаление", f"Удалить пункт #{pid}?"):
                    self.planner.delete_plan_item(pid)
                    self.refresh_list()

        def show_stats(self):
            d = self.date_var.get()
            stats = self.planner.get_day_stats(d)
            msg = f"📊 Статистика за {d}\n"
            msg += f"Калории: {stats['total_calories']:.1f}\n"
            msg += f"Белки: {stats['total_protein']:.1f} г\n"
            msg += f"Жиры: {stats['total_fat']:.1f} г\n"
            msg += f"Углеводы: {stats['total_carbs']:.1f} г\n"
            if stats['details']:
                msg += "\nДетали:\n"
                for d in stats['details']:
                    msg += f"  {d['meal_type']}: {d['food']} x{d['portion']:.1f} = {d['calories']:.1f} ккал\n"
            messagebox.showinfo("Статистика", msg)

        def random_menu(self):
            d = self.date_var.get()
            try:
                limit = float(input("Лимит калорий (по умолчанию 2000): ") or "2000")
            except:
                limit = 2000
            items = self.planner.generate_random_menu(d, limit)
            self.refresh_list()
            messagebox.showinfo("Случайное меню", f"Сгенерировано {len(items)} блюд на {d}")

        def export_csv(self):
            filepath = filedialog.asksaveasfilename(defaultextension=".csv", filetypes=[("CSV", "*.csv")])
            if filepath:
                d = self.date_var.get()
                self.planner.export_csv(filepath, d)
                messagebox.showinfo("Экспорт", f"Сохранено в {filepath}")

if __name__ == "__main__":
    cli()
