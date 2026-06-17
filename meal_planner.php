<?php
// meal_planner.php - Планировщик питания на PHP (CLI + веб)
// CLI: php meal_planner.php add-food --name="Овсянка" --calories=120 --protein=5 --fat=3 --carbs=20

$dataFile = 'meal_data.json';

function loadData() {
    global $dataFile;
    if (!file_exists($dataFile)) {
        return ['foods' => [], 'plans' => [], 'next_id' => 1];
    }
    $json = file_get_contents($dataFile);
    $data = json_decode($json, true);
    if (!$data) $data = ['foods' => [], 'plans' => [], 'next_id' => 1];
    return $data;
}

function saveData($data) {
    global $dataFile;
    file_put_contents($dataFile, json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
}

function addFood(&$data, $name, $calories, $protein, $fat, $carbs, $description) {
    $food = ['name' => $name, 'calories' => $calories, 'protein' => $protein, 'fat' => $fat, 'carbs' => $carbs, 'description' => $description];
    $data['foods'][] = $food;
    saveData($data);
    return $food;
}

function getFood($data, $name) {
    foreach ($data['foods'] as $f) {
        if (strcasecmp($f['name'], $name) == 0) return $f;
    }
    return null;
}

function suggestFoods($data, $prefix) {
    $result = [];
    foreach ($data['foods'] as $f) {
        if (stripos($f['name'], $prefix) === 0) $result[] = $f['name'];
    }
    return $result;
}

function addMealPlan(&$data, $foodName, $portion, $date, $mealType, $notes) {
    if (!$date) $date = date('Y-m-d');
    $id = $data['next_id']++;
    $item = ['id' => $id, 'foodName' => $foodName, 'portion' => $portion, 'date' => $date, 'mealType' => $mealType, 'notes' => $notes];
    $data['plans'][] = $item;
    saveData($data);
    return $item;
}

function deletePlanItem(&$data, $id) {
    $filtered = array_filter($data['plans'], function($p) use ($id) { return $p['id'] != $id; });
    if (count($filtered) < count($data['plans'])) {
        $data['plans'] = array_values($filtered);
        saveData($data);
        return true;
    }
    return false;
}

function getPlanForDate($data, $dateStr) {
    $result = array_filter($data['plans'], function($p) use ($dateStr) { return $p['date'] == $dateStr; });
    usort($result, function($a, $b) {
        $order = ['breakfast'=>0, 'lunch'=>1, 'dinner'=>2, 'snack'=>3];
        return $order[$a['mealType']] - $order[$b['mealType']];
    });
    return array_values($result);
}

function getDayStats($data, $dateStr) {
    $items = getPlanForDate($data, $dateStr);
    $totalCal = 0; $totalProt = 0; $totalFat = 0; $totalCarbs = 0;
    $details = [];
    foreach ($items as $p) {
        $food = getFood($data, $p['foodName']);
        if ($food) {
            $factor = $p['portion'];
            $totalCal += $food['calories'] * $factor;
            $totalProt += $food['protein'] * $factor;
            $totalFat += $food['fat'] * $factor;
            $totalCarbs += $food['carbs'] * $factor;
            $details[] = ['mealType' => $p['mealType'], 'food' => $food['name'], 'portion' => $p['portion'], 'calories' => $food['calories'] * $factor];
        } else {
            $details[] = ['mealType' => $p['mealType'], 'food' => $p['foodName'], 'portion' => $p['portion'], 'calories' => 0, 'warning' => 'Неизвестный продукт'];
        }
    }
    return ['totalCalories' => $totalCal, 'totalProtein' => $totalProt, 'totalFat' => $totalFat, 'totalCarbs' => $totalCarbs, 'details' => $details];
}

function generateRandomMenu(&$data, $dateStr, $caloriesLimit) {
    if (empty($data['foods'])) return [];
    shuffle($data['foods']);
    $items = [];
    $totalCal = 0;
    $mealTypes = ['breakfast', 'lunch', 'dinner', 'snack'];
    $idx = 0;
    foreach ($data['foods'] as $food) {
        $portionChoices = [0.5, 0.75, 1.0, 1.25, 1.5];
        $portion = $portionChoices[array_rand($portionChoices)];
        $cal = $food['calories'] * $portion;
        if ($totalCal + $cal <= $caloriesLimit) {
            $mtype = $mealTypes[$idx % count($mealTypes)];
            $idx++;
            $item = ['id' => $data['next_id']++, 'foodName' => $food['name'], 'portion' => $portion, 'date' => $dateStr, 'mealType' => $mtype, 'notes' => 'Сгенерировано автоматически'];
            $data['plans'][] = $item;
            $items[] = $item;
            $totalCal += $cal;
        }
        if ($totalCal >= $caloriesLimit * 0.8) break;
    }
    saveData($data);
    return $items;
}

function exportCSV($data, $filepath, $dateStr) {
    $f = fopen($filepath, 'w');
    fputcsv($f, ['Date', 'Meal Type', 'Food', 'Portion', 'Calories', 'Protein', 'Fat', 'Carbs']);
    $plans = $dateStr ? getPlanForDate($data, $dateStr) : $data['plans'];
    foreach ($plans as $p) {
        $food = getFood($data, $p['foodName']);
        $c = $food ? $food['calories'] * $p['portion'] : 0;
        $pr = $food ? $food['protein'] * $p['portion'] : 0;
        $fa = $food ? $food['fat'] * $p['portion'] : 0;
        $ca = $food ? $food['carbs'] * $p['portion'] : 0;
        fputcsv($f, [$p['date'], $p['mealType'], $p['foodName'], $p['portion'], $c, $pr, $fa, $ca]);
    }
    fclose($f);
}

function importCSV(&$data, $filepath) {
    $f = fopen($filepath, 'r');
    $header = fgetcsv($f);
    while (($row = fgetcsv($f)) !== false) {
        if (count($row) < 8) continue;
        $date = $row[0];
        $mealType = $row[1];
        $foodName = $row[2];
        $portion = (float)$row[3];
        addMealPlan($data, $foodName, $portion, $date, $mealType, 'Импортировано из CSV');
    }
    fclose($f);
}

// ========== CLI ==========
if (php_sapi_name() === 'cli') {
    $options = getopt("", ["cmd:", "name:", "calories:", "protein:", "fat:", "carbs:", "description:", "food:", "portion:", "date:", "meal-type:", "notes:", "id:", "calories-limit:", "output:", "file:"]);
    $cmd = $options['cmd'] ?? null;
    $data = loadData();
    switch ($cmd) {
        case 'add-food':
            $name = $options['name'] ?? '';
            $calories = isset($options['calories']) ? (float)$options['calories'] : 0;
            $protein = isset($options['protein']) ? (float)$options['protein'] : 0;
            $fat = isset($options['fat']) ? (float)$options['fat'] : 0;
            $carbs = isset($options['carbs']) ? (float)$options['carbs'] : 0;
            $desc = $options['description'] ?? '';
            if ($name && $calories > 0) {
                addFood($data, $name, $calories, $protein, $fat, $carbs, $desc);
                echo "✅ Продукт '$name' добавлен\n";
            } else {
                echo "Укажите --name и --calories\n";
            }
            break;
        case 'add-meal':
            $foodName = $options['food'] ?? '';
            $portion = isset($options['portion']) ? (float)$options['portion'] : 1;
            $date = $options['date'] ?? null;
            $mealType = $options['meal-type'] ?? 'lunch';
            $notes = $options['notes'] ?? '';
            if ($foodName) {
                if (!getFood($data, $foodName)) {
                    echo "Продукт не найден\n";
                    break;
                }
                $item = addMealPlan($data, $foodName, $portion, $date, $mealType, $notes);
                echo "✅ Приём пищи #{$item['id']} добавлен\n";
            } else {
                echo "Укажите --food\n";
            }
            break;
        case 'list':
            $date = $options['date'] ?? date('Y-m-d');
            $items = getPlanForDate($data, $date);
            if (empty($items)) {
                echo "Нет записей на $date\n";
            } else {
                $stats = getDayStats($data, $date);
                printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал");
                foreach ($items as $p) {
                    $food = getFood($data, $p['foodName']);
                    $cal = $food ? $food['calories'] * $p['portion'] : 0;
                    printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", $p['id'], $p['mealType'], $p['foodName'], $p['portion'], $cal);
                }
                printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n", $stats['totalCalories'], $stats['totalProtein'], $stats['totalFat'], $stats['totalCarbs']);
            }
            break;
        case 'stats':
            $date = $options['date'] ?? date('Y-m-d');
            $stats = getDayStats($data, $date);
            echo "📊 Статистика за $date\n";
            echo "Калории: " . number_format($stats['totalCalories'], 1) . "\n";
            echo "Белки: " . number_format($stats['totalProtein'], 1) . " г\n";
            echo "Жиры: " . number_format($stats['totalFat'], 1) . " г\n";
            echo "Углеводы: " . number_format($stats['totalCarbs'], 1) . " г\n";
            if (!empty($stats['details'])) {
                echo "Детали:\n";
                foreach ($stats['details'] as $d) {
                    echo "  {$d['mealType']}: {$d['food']} x{$d['portion']} = " . number_format($d['calories'], 1) . " ккал\n";
                }
            }
            break;
        case 'delete':
            $id = isset($options['id']) ? (int)$options['id'] : 0;
            if ($id && deletePlanItem($data, $id)) {
                echo "✅ Пункт #$id удалён\n";
            } else {
                echo "❌ Пункт #$id не найден\n";
            }
            break;
        case 'random':
            $date = $options['date'] ?? date('Y-m-d');
            $limit = isset($options['calories-limit']) ? (float)$options['calories-limit'] : 2000;
            $items = generateRandomMenu($data, $date, $limit);
            echo "✅ Сгенерировано " . count($items) . " блюд на $date\n";
            break;
        case 'export':
            $output = $options['output'] ?? null;
            $date = $options['date'] ?? null;
            if ($output) {
                exportCSV($data, $output, $date);
                echo "Экспортировано в $output\n";
            } else {
                echo "Укажите --output\n";
            }
            break;
        case 'import':
            $file = $options['file'] ?? null;
            if ($file) {
                importCSV($data, $file);
                echo "Импортировано из $file\n";
            } else {
                echo "Укажите --file\n";
            }
            break;
        default:
            interactiveMode($data);
            break;
    }
    exit;
}

// ========== ИНТЕРАКТИВНЫЙ РЕЖИМ ==========
function interactiveMode(&$data) {
    while (true) {
        echo "\n🍽️ Планировщик питания (интерактивный)\n";
        echo "1. Добавить продукт\n";
        echo "2. Добавить приём пищи\n";
        echo "3. Показать план на день\n";
        echo "4. Статистика за день\n";
        echo "5. Удалить пункт плана\n";
        echo "6. Сгенерировать случайное меню\n";
        echo "7. Экспорт CSV\n";
        echo "8. Импорт CSV\n";
        echo "0. Выход\n";
        echo "Выберите действие: ";
        $choice = trim(fgets(STDIN));
        switch ($choice) {
            case '0': return;
            case '1':
                echo "Название продукта: ";
                $name = trim(fgets(STDIN));
                if (!$name) { echo "Название обязательно\n"; break; }
                echo "Калории: ";
                $cal = (float)trim(fgets(STDIN));
                echo "Белки (г): ";
                $prot = (float)trim(fgets(STDIN));
                echo "Жиры (г): ";
                $fat = (float)trim(fgets(STDIN));
                echo "Углеводы (г): ";
                $carbs = (float)trim(fgets(STDIN));
                echo "Описание (необязательно): ";
                $desc = trim(fgets(STDIN));
                addFood($data, $name, $cal, $prot, $fat, $carbs, $desc);
                echo "✅ Продукт '$name' добавлен\n";
                break;
            case '2':
                echo "Название продукта: ";
                $fname = trim(fgets(STDIN));
                if (!$fname) { echo "Введите название\n"; break; }
                if (!getFood($data, $fname)) { echo "Продукт не найден\n"; break; }
                echo "Количество порций (по умолчанию 1): ";
                $portionStr = trim(fgets(STDIN));
                $portion = $portionStr ? (float)$portionStr : 1;
                echo "Дата (ГГГГ-ММ-ДД, Enter сегодня): ";
                $date = trim(fgets(STDIN));
                if (!$date) $date = date('Y-m-d');
                echo "Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ";
                $mealType = trim(fgets(STDIN));
                if (!$mealType) $mealType = 'lunch';
                echo "Заметки: ";
                $notes = trim(fgets(STDIN));
                $item = addMealPlan($data, $fname, $portion, $date, $mealType, $notes);
                echo "✅ Добавлен приём пищи #{$item['id']}\n";
                break;
            case '3':
                echo "Дата (ГГГГ-ММ-ДД, Enter сегодня): ";
                $d = trim(fgets(STDIN));
                if (!$d) $d = date('Y-m-d');
                $items = getPlanForDate($data, $d);
                if (empty($items)) {
                    echo "Нет записей на $d\n";
                } else {
                    $stats = getDayStats($data, $d);
                    printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал");
                    foreach ($items as $p) {
                        $food = getFood($data, $p['foodName']);
                        $cal = $food ? $food['calories'] * $p['portion'] : 0;
                        printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", $p['id'], $p['mealType'], $p['foodName'], $p['portion'], $cal);
                    }
                    printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n", $stats['totalCalories'], $stats['totalProtein'], $stats['totalFat'], $stats['totalCarbs']);
                }
                break;
            case '4':
                echo "Дата (ГГГГ-ММ-ДД, Enter сегодня): ";
                $d2 = trim(fgets(STDIN));
                if (!$d2) $d2 = date('Y-m-d');
                $stats = getDayStats($data, $d2);
                echo "📊 Статистика за $d2\n";
                echo "Калории: " . number_format($stats['totalCalories'], 1) . "\n";
                echo "Белки: " . number_format($stats['totalProtein'], 1) . " г\n";
                echo "Жиры: " . number_format($stats['totalFat'], 1) . " г\n";
                echo "Углеводы: " . number_format($stats['totalCarbs'], 1) . " г\n";
                if (!empty($stats['details'])) {
                    echo "Детали:\n";
                    foreach ($stats['details'] as $det) {
                        echo "  {$det['mealType']}: {$det['food']} x{$det['portion']} = " . number_format($det['calories'], 1) . " ккал\n";
                    }
                }
                break;
            case '5':
                echo "ID пункта для удаления: ";
                $id = (int)trim(fgets(STDIN));
                if (deletePlanItem($data, $id)) {
                    echo "✅ Удалено\n";
                } else {
                    echo "❌ Не найдено\n";
                }
                break;
            case '6':
                echo "Дата (ГГГГ-ММ-ДД, Enter сегодня): ";
                $d3 = trim(fgets(STDIN));
                if (!$d3) $d3 = date('Y-m-d');
                echo "Лимит калорий (по умолчанию 2000): ";
                $limitStr = trim(fgets(STDIN));
                $limit = $limitStr ? (float)$limitStr : 2000;
                $items = generateRandomMenu($data, $d3, $limit);
                echo "✅ Сгенерировано " . count($items) . " блюд\n";
                break;
            case '7':
                echo "Имя файла (CSV): ";
                $file = trim(fgets(STDIN));
                if (!$file) $file = 'meal_plan.csv';
                echo "Дата (оставьте пустым для всех): ";
                $expDate = trim(fgets(STDIN));
                exportCSV($data, $file, $expDate);
                echo "Экспортировано в $file\n";
                break;
            case '8':
                echo "Имя файла (CSV): ";
                $impFile = trim(fgets(STDIN));
                if (!$impFile) { echo "Укажите файл\n"; break; }
                importCSV($data, $impFile);
                echo "Импортировано из $impFile\n";
                break;
            default:
                echo "Неверный выбор\n";
        }
    }
}

// ========== ВЕБ-ИНТЕРФЕЙС ==========
if (php_sapi_name() !== 'cli') {
    $data = loadData();
    ?>
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <title>🍽️ Планировщик питания (PHP)</title>
        <style>
            body { font-family: 'Segoe UI', sans-serif; background: #f4f7fb; margin: 20px; }
            .container { max-width: 900px; margin: 0 auto; background: white; padding: 20px; border-radius: 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
            table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background: #2c3e50; color: white; }
            .form-row { margin: 10px 0; }
            .form-row label { display: inline-block; width: 120px; }
            input, select, button { padding: 6px; margin: 2px; }
            button { background: #3498db; color: white; border: none; border-radius: 4px; cursor: pointer; }
            .stats { margin-top: 20px; }
        </style>
    </head>
    <body>
    <div class="container">
        <h1>🍽️ Планировщик питания</h1>
        <h3>Добавить продукт</h3>
        <form method="GET">
            <div class="form-row"><label>Название:</label><input type="text" name="name" required></div>
            <div class="form-row"><label>Калории:</label><input type="number" step="any" name="calories" required></div>
            <div class="form-row"><label>Белки (г):</label><input type="number" step="any" name="protein" value="0"></div>
            <div class="form-row"><label>Жиры (г):</label><input type="number" step="any" name="fat" value="0"></div>
            <div class="form-row"><label>Углеводы (г):</label><input type="number" step="any" name="carbs" value="0"></div>
            <div class="form-row"><label>Описание:</label><input type="text" name="description"></div>
            <button type="submit" name="action" value="add-food">➕ Добавить продукт</button>
        </form>

        <h3>Добавить приём пищи</h3>
        <form method="GET">
            <div class="form-row"><label>Продукт:</label><input type="text" name="food" required></div>
            <div class="form-row"><label>Порция:</label><input type="number" step="any" name="portion" value="1"></div>
            <div class="form-row"><label>Дата:</label><input type="date" name="date" value="<?= date('Y-m-d') ?>"></div>
            <div class="form-row"><label>Тип приёма:</label>
                <select name="meal-type">
                    <option value="breakfast">breakfast</option>
                    <option value="lunch" selected>lunch</option>
                    <option value="dinner">dinner</option>
                    <option value="snack">snack</option>
                </select>
            </div>
            <div class="form-row"><label>Заметки:</label><input type="text" name="notes"></div>
            <button type="submit" name="action" value="add-meal">➕ Добавить приём</button>
        </form>

        <h3>План на день</h3>
        <form method="GET">
            <label>Дата: <input type="date" name="date" value="<?= $_GET['date'] ?? date('Y-m-d') ?>"></label>
            <button type="submit" name="action" value="list">Показать</button>
        </form>

        <?php
        $action = $_GET['action'] ?? null;
        if ($action == 'add-food' && isset($_GET['name']) && isset($_GET['calories'])) {
            $name = $_GET['name'];
            $cal = (float)$_GET['calories'];
            $prot = isset($_GET['protein']) ? (float)$_GET['protein'] : 0;
            $fat = isset($_GET['fat']) ? (float)$_GET['fat'] : 0;
            $carbs = isset($_GET['carbs']) ? (float)$_GET['carbs'] : 0;
            $desc = $_GET['description'] ?? '';
            addFood($data, $name, $cal, $prot, $fat, $carbs, $desc);
            echo "<div style='background:#d5f5e3; padding:10px;'>✅ Добавлен продукт '$name'</div>";
        }
        if ($action == 'add-meal' && isset($_GET['food'])) {
            $foodName = $_GET['food'];
            $portion = isset($_GET['portion']) ? (float)$_GET['portion'] : 1;
            $date = $_GET['date'] ?? date('Y-m-d');
            $mealType = $_GET['meal-type'] ?? 'lunch';
            $notes = $_GET['notes'] ?? '';
            if (getFood($data, $foodName)) {
                addMealPlan($data, $foodName, $portion, $date, $mealType, $notes);
                echo "<div style='background:#d5f5e3; padding:10px;'>✅ Добавлен приём пищи</div>";
            } else {
                echo "<div style='background:#ffebee; padding:10px; color:#c62828;'>❌ Продукт не найден</div>";
            }
        }
        if ($action == 'list' || !isset($_GET['action'])) {
            $date = $_GET['date'] ?? date('Y-m-d');
            $items = getPlanForDate($data, $date);
            if (empty($items)) {
                echo "<p>Нет записей на $date</p>";
            } else {
                $stats = getDayStats($data, $date);
                echo "<h3>📅 План на $date</h3>";
                echo "<table><tr><th>ID</th><th>Приём</th><th>Продукт</th><th>Порция</th><th>Ккал</th></tr>";
                foreach ($items as $p) {
                    $food = getFood($data, $p['foodName']);
                    $cal = $food ? $food['calories'] * $p['portion'] : 0;
                    echo "<tr><td>{$p['id']}</td><td>{$p['mealType']}</td><td>{$p['foodName']}</td><td>{$p['portion']}</td><td>" . number_format($cal, 1) . "</td></tr>";
                }
                echo "</table>";
                echo "<p>Итого: " . number_format($stats['totalCalories'], 1) . " ккал, Б: " . number_format($stats['totalProtein'], 1) . ", Ж: " . number_format($stats['totalFat'], 1) . ", У: " . number_format($stats['totalCarbs'], 1) . "</p>";
            }
        }

        // Статистика
        $statsAll = getDayStats($data, date('Y-m-d'));
        echo "<div class='stats'><h3>📊 Статистика за сегодня</h3>";
        echo "<p>Калории: " . number_format($statsAll['totalCalories'], 1) . "</p>";
        echo "<p>Белки: " . number_format($statsAll['totalProtein'], 1) . " г</p>";
        echo "<p>Жиры: " . number_format($statsAll['totalFat'], 1) . " г</p>";
        echo "<p>Углеводы: " . number_format($statsAll['totalCarbs'], 1) . " г</p>";
        echo "</div>";

        // Рекорды, экспорт, и т.д.
        ?>
        <p>
            <a href="?action=random&date=<?= date('Y-m-d') ?>&calories-limit=2000">🎲 Сгенерировать случайное меню</a><br>
            <a href="?action=export&output=plan.csv">📤 Экспорт CSV</a>
        </p>
        <?php
        if ($action == 'random' && isset($_GET['date'])) {
            $date = $_GET['date'] ?? date('Y-m-d');
            $limit = isset($_GET['calories-limit']) ? (float)$_GET['calories-limit'] : 2000;
            generateRandomMenu($data, $date, $limit);
            echo "<div style='background:#d5f5e3; padding:10px;'>✅ Сгенерировано случайное меню</div>";
            header("Location: ?date=$date");
            exit;
        }
        if ($action == 'export' && isset($_GET['output'])) {
            exportCSV($data, $_GET['output'], null);
            echo "<div style='background:#d5f5e3; padding:10px;'>✅ Экспортировано в " . $_GET['output'] . "</div>";
        }
        ?>
    </div>
    </body>
    </html>
    <?php
}
