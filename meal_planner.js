#!/usr/bin/env node
/**
 * meal_planner.js - Планировщик питания на JavaScript (Node.js CLI)
 */
const fs = require('fs');
const path = require('path');
const { program } = require('commander');
const { v4: uuidv4 } = require('uuid');

const DATA_FILE = path.join(__dirname, 'meal_data.json');

class Food {
    constructor(name, calories, protein, fat, carbs, description = '') {
        this.name = name;
        this.calories = calories;
        this.protein = protein || 0;
        this.fat = fat || 0;
        this.carbs = carbs || 0;
        this.description = description;
    }
}

class MealPlanItem {
    constructor(foodName, portion, date, mealType, notes = '') {
        this.id = uuidv4();
        this.foodName = foodName;
        this.portion = portion;
        this.date = date || new Date().toISOString().slice(0,10);
        this.mealType = mealType || 'lunch';
        this.notes = notes;
    }
}

class MealPlanner {
    constructor() {
        this.foods = [];
        this.plans = [];
        this.load();
    }

    load() {
        if (fs.existsSync(DATA_FILE)) {
            try {
                const data = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
                this.foods = data.foods || [];
                this.plans = data.plans || [];
            } catch {}
        }
    }

    save() {
        fs.writeFileSync(DATA_FILE, JSON.stringify({ foods: this.foods, plans: this.plans }, null, 2));
    }

    addFood(name, calories, protein, fat, carbs, description) {
        const food = new Food(name, calories, protein, fat, carbs, description);
        this.foods.push(food);
        this.save();
        return food;
    }

    getFood(name) {
        return this.foods.find(f => f.name.toLowerCase() === name.toLowerCase());
    }

    suggestFoods(prefix) {
        return this.foods.filter(f => f.name.toLowerCase().startsWith(prefix.toLowerCase())).map(f => f.name);
    }

    addMealPlan(foodName, portion, date, mealType, notes) {
        if (!date) date = new Date().toISOString().slice(0,10);
        const item = new MealPlanItem(foodName, portion, date, mealType, notes);
        this.plans.push(item);
        this.save();
        return item;
    }

    deletePlanItem(id) {
        const idx = this.plans.findIndex(p => p.id === id);
        if (idx === -1) return false;
        this.plans.splice(idx, 1);
        this.save();
        return true;
    }

    getPlanForDate(dateStr) {
        return this.plans.filter(p => p.date === dateStr).sort((a, b) => a.mealType.localeCompare(b.mealType));
    }

    getDayStats(dateStr) {
        const items = this.getPlanForDate(dateStr);
        let totalCal = 0, totalProtein = 0, totalFat = 0, totalCarbs = 0;
        const details = [];
        items.forEach(item => {
            const food = this.getFood(item.foodName);
            if (food) {
                const factor = item.portion;
                totalCal += food.calories * factor;
                totalProtein += food.protein * factor;
                totalFat += food.fat * factor;
                totalCarbs += food.carbs * factor;
                details.push({
                    food: food.name,
                    portion: item.portion,
                    calories: food.calories * factor,
                    mealType: item.mealType
                });
            } else {
                details.push({
                    food: item.foodName,
                    portion: item.portion,
                    calories: 0,
                    mealType: item.mealType,
                    warning: 'Неизвестный продукт'
                });
            }
        });
        return { totalCalories: totalCal, totalProtein, totalFat, totalCarbs, details };
    }

    generateRandomMenu(dateStr, caloriesLimit) {
        if (!this.foods.length) return [];
        const available = [...this.foods];
        // перемешиваем
        for (let i = available.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [available[i], available[j]] = [available[j], available[i]];
        }
        let totalCal = 0;
        const selected = [];
        const mealTypes = ['breakfast', 'lunch', 'dinner', 'snack'];
        let idx = 0;
        for (const food of available) {
            const portion = [0.5, 0.75, 1.0, 1.25, 1.5][Math.floor(Math.random() * 5)];
            const cal = food.calories * portion;
            if (totalCal + cal <= caloriesLimit) {
                selected.push({ food, portion });
                totalCal += cal;
            }
            if (totalCal >= caloriesLimit * 0.8) break;
        }
        const items = [];
        for (const sel of selected) {
            const mtype = mealTypes[idx % mealTypes.length];
            idx++;
            const item = new MealPlanItem(sel.food.name, sel.portion, dateStr, mtype, 'Сгенерировано автоматически');
            this.plans.push(item);
            items.push(item);
        }
        this.save();
        return items;
    }

    exportCSV(filepath, dateStr) {
        const lines = ['Date,Meal Type,Food,Portion,Calories,Protein,Fat,Carbs'];
        const plans = dateStr ? this.getPlanForDate(dateStr) : this.plans;
        plans.forEach(p => {
            const food = this.getFood(p.foodName);
            const c = food ? food.calories * p.portion : 0;
            const pr = food ? food.protein * p.portion : 0;
            const fa = food ? food.fat * p.portion : 0;
            const ca = food ? food.carbs * p.portion : 0;
            lines.push(`${p.date},${p.mealType},${p.foodName},${p.portion},${c},${pr},${fa},${ca}`);
        });
        fs.writeFileSync(filepath, lines.join('\n'));
    }

    importCSV(filepath) {
        const content = fs.readFileSync(filepath, 'utf8');
        const lines = content.split('\n').filter(line => line.trim());
        const header = lines[0].split(',');
        for (let i = 1; i < lines.length; i++) {
            const parts = lines[i].split(',');
            if (parts.length < 8) continue;
            const date = parts[0];
            const mealType = parts[1];
            const foodName = parts[2];
            const portion = parseFloat(parts[3]);
            this.addMealPlan(foodName, portion, date, mealType, 'Импортировано из CSV');
        }
    }
}

program
    .command('add-food <name>')
    .option('--calories <calories>', 'Калории', parseFloat)
    .option('--protein <protein>', 'Белки', parseFloat, 0)
    .option('--fat <fat>', 'Жиры', parseFloat, 0)
    .option('--carbs <carbs>', 'Углеводы', parseFloat, 0)
    .option('--description <description>', 'Описание')
    .action((name, options) => {
        const planner = new MealPlanner();
        planner.addFood(name, options.calories, options.protein, options.fat, options.carbs, options.description);
        console.log(`✅ Продукт '${name}' добавлен`);
    });

program
    .command('add-meal')
    .requiredOption('--food <foodName>', 'Название продукта')
    .option('--portion <portion>', 'Количество порций', parseFloat, 1)
    .option('--date <date>', 'Дата (ГГГГ-ММ-ДД)')
    .option('--meal-type <mealType>', 'breakfast/lunch/dinner/snack', 'lunch')
    .option('--notes <notes>', 'Заметки')
    .action((options) => {
        const planner = new MealPlanner();
        const item = planner.addMealPlan(options.food, options.portion, options.date, options.mealType, options.notes);
        console.log(`✅ Приём пищи ${item.id} добавлен`);
    });

program
    .command('list')
    .option('--date <date>', 'Дата')
    .action((options) => {
        const planner = new MealPlanner();
        const d = options.date || new Date().toISOString().slice(0,10);
        const items = planner.getPlanForDate(d);
        if (!items.length) {
            console.log(`Нет записей на ${d}`);
            return;
        }
        const stats = planner.getDayStats(d);
        console.log(`📅 План на ${d}`);
        console.log('ID'.padEnd(36) + 'Приём'.padEnd(10) + 'Продукт'.padEnd(20) + 'Порция'.padEnd(8) + 'Ккал');
        items.forEach(p => {
            const food = planner.getFood(p.foodName);
            const cal = food ? food.calories * p.portion : 0;
            console.log(`${p.id.padEnd(36)} ${p.mealType.padEnd(10)} ${p.foodName.padEnd(20)} ${p.portion.toFixed(1).padEnd(8)} ${cal.toFixed(1)}`);
        });
        console.log(`Итого: ${stats.totalCalories.toFixed(1)} ккал, Б: ${stats.totalProtein.toFixed(1)}, Ж: ${stats.totalFat.toFixed(1)}, У: ${stats.totalCarbs.toFixed(1)}`);
    });

program
    .command('stats')
    .option('--date <date>', 'Дата')
    .action((options) => {
        const planner = new MealPlanner();
        const d = options.date || new Date().toISOString().slice(0,10);
        const stats = planner.getDayStats(d);
        console.log(`📊 Статистика за ${d}`);
        console.log(`Калории: ${stats.totalCalories.toFixed(1)}`);
        console.log(`Белки: ${stats.totalProtein.toFixed(1)} г`);
        console.log(`Жиры: ${stats.totalFat.toFixed(1)} г`);
        console.log(`Углеводы: ${stats.totalCarbs.toFixed(1)} г`);
        if (stats.details.length) {
            console.log('Детали:');
            stats.details.forEach(d => {
                console.log(`  ${d.mealType}: ${d.food} x${d.portion.toFixed(1)} = ${d.calories.toFixed(1)} ккал`);
            });
        }
    });

program
    .command('delete <id>')
    .action((id) => {
        const planner = new MealPlanner();
        if (planner.deletePlanItem(id)) {
            console.log(`✅ Пункт ${id} удалён`);
        } else {
            console.log(`❌ Пункт ${id} не найден`);
        }
    });

program
    .command('random')
    .option('--calories-limit <limit>', 'Лимит калорий', parseFloat, 2000)
    .option('--date <date>', 'Дата')
    .action((options) => {
        const planner = new MealPlanner();
        const d = options.date || new Date().toISOString().slice(0,10);
        const items = planner.generateRandomMenu(d, options.caloriesLimit);
        console.log(`✅ Сгенерировано ${items.length} блюд на ${d}`);
    });

program
    .command('export')
    .requiredOption('-o, --output <file>', 'Имя CSV файла')
    .option('--date <date>', 'Дата (если не указана, то все)')
    .action((options) => {
        const planner = new MealPlanner();
        planner.exportCSV(options.output, options.date);
        console.log(`Экспортировано в ${options.output}`);
    });

program
    .command('import')
    .requiredOption('-f, --file <file>', 'CSV файл')
    .action((options) => {
        const planner = new MealPlanner();
        planner.importCSV(options.file);
        console.log(`Импортировано из ${options.file}`);
    });

program
    .command('interactive')
    .description('Интерактивный режим')
    .action(() => {
        const planner = new MealPlanner();
        const readline = require('readline');
        const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
        const prompt = (q) => new Promise(resolve => rl.question(q, resolve));

        (async () => {
            while (true) {
                console.log('\n🍽️ Планировщик питания (интерактивный)');
                console.log('1. Добавить продукт');
                console.log('2. Добавить приём пищи');
                console.log('3. Показать план на день');
                console.log('4. Статистика за день');
                console.log('5. Удалить пункт плана');
                console.log('6. Сгенерировать случайное меню');
                console.log('7. Экспорт CSV');
                console.log('8. Импорт CSV');
                console.log('0. Выход');
                const choice = await prompt('Выберите действие: ');
                switch (choice.trim()) {
                    case '0': rl.close(); return;
                    case '1': {
                        const name = await prompt('Название продукта: ');
                        if (!name) { console.log('Название обязательно'); break; }
                        const cal = parseFloat(await prompt('Калории: '));
                        const prot = parseFloat(await prompt('Белки (г): ') || '0');
                        const fat = parseFloat(await prompt('Жиры (г): ') || '0');
                        const carbs = parseFloat(await prompt('Углеводы (г): ') || '0');
                        const desc = await prompt('Описание (необязательно): ');
                        planner.addFood(name, cal, prot, fat, carbs, desc);
                        console.log(`✅ Продукт '${name}' добавлен`);
                        break;
                    }
                    case '2': {
                        const foodName = await prompt('Название продукта: ');
                        if (!foodName) { console.log('Введите название'); break; }
                        if (!planner.getFood(foodName)) {
                            console.log('Продукт не найден. Сначала добавьте его.');
                            break;
                        }
                        const portion = parseFloat(await prompt('Количество порций (по умолчанию 1): ') || '1');
                        const date = await prompt('Дата (ГГГГ-ММ-ДД, Enter сегодня): ') || new Date().toISOString().slice(0,10);
                        const mealType = await prompt('Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ') || 'lunch';
                        const notes = await prompt('Заметки: ');
                        const item = planner.addMealPlan(foodName, portion, date, mealType, notes);
                        console.log(`✅ Добавлен приём пищи ${item.id}`);
                        break;
                    }
                    case '3': {
                        const d = await prompt('Дата (ГГГГ-ММ-ДД, Enter сегодня): ') || new Date().toISOString().slice(0,10);
                        const items = planner.getPlanForDate(d);
                        if (!items.length) { console.log(`Нет записей на ${d}`); break; }
                        const stats = planner.getDayStats(d);
                        console.log(`📅 План на ${d}`);
                        console.log('ID'.padEnd(36) + 'Приём'.padEnd(10) + 'Продукт'.padEnd(20) + 'Порция'.padEnd(8) + 'Ккал');
                        items.forEach(p => {
                            const food = planner.getFood(p.foodName);
                            const cal = food ? food.calories * p.portion : 0;
                            console.log(`${p.id.padEnd(36)} ${p.mealType.padEnd(10)} ${p.foodName.padEnd(20)} ${p.portion.toFixed(1).padEnd(8)} ${cal.toFixed(1)}`);
                        });
                        console.log(`Итого: ${stats.totalCalories.toFixed(1)} ккал, Б: ${stats.totalProtein.toFixed(1)}, Ж: ${stats.totalFat.toFixed(1)}, У: ${stats.totalCarbs.toFixed(1)}`);
                        break;
                    }
                    case '4': {
                        const d = await prompt('Дата (ГГГГ-ММ-ДД, Enter сегодня): ') || new Date().toISOString().slice(0,10);
                        const stats = planner.getDayStats(d);
                        console.log(`📊 Статистика за ${d}`);
                        console.log(`Калории: ${stats.totalCalories.toFixed(1)}`);
                        console.log(`Белки: ${stats.totalProtein.toFixed(1)} г`);
                        console.log(`Жиры: ${stats.totalFat.toFixed(1)} г`);
                        console.log(`Углеводы: ${stats.totalCarbs.toFixed(1)} г`);
                        if (stats.details.length) {
                            console.log('Детали:');
                            stats.details.forEach(d => {
                                console.log(`  ${d.mealType}: ${d.food} x${d.portion.toFixed(1)} = ${d.calories.toFixed(1)} ккал`);
                            });
                        }
                        break;
                    }
                    case '5': {
                        const id = await prompt('ID пункта для удаления: ');
                        if (planner.deletePlanItem(id)) {
                            console.log('✅ Удалено');
                        } else {
                            console.log('❌ Не найдено');
                        }
                        break;
                    }
                    case '6': {
                        const d = await prompt('Дата (ГГГГ-ММ-ДД, Enter сегодня): ') || new Date().toISOString().slice(0,10);
                        const limit = parseFloat(await prompt('Лимит калорий (по умолчанию 2000): ') || '2000');
                        const items = planner.generateRandomMenu(d, limit);
                        console.log(`✅ Сгенерировано ${items.length} блюд`);
                        break;
                    }
                    case '7': {
                        const file = await prompt('Имя файла (CSV): ') || 'meal_plan.csv';
                        const d = await prompt('Дата (оставьте пустым для всех): ') || undefined;
                        planner.exportCSV(file, d);
                        console.log(`Экспортировано в ${file}`);
                        break;
                    }
                    case '8': {
                        const file = await prompt('Имя файла (CSV): ');
                        if (!file) { console.log('Укажите файл'); break; }
                        planner.importCSV(file);
                        console.log(`Импортировано из ${file}`);
                        break;
                    }
                    default: console.log('Неверный выбор');
                }
            }
        })();
    });

if (process.argv.length <= 2) {
    process.argv.push('interactive');
}
program.parse(process.argv);
