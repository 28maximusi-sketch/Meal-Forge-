// meal_planner.go - Планировщик питания на Go (CLI)
package main

import (
	"bufio"
	"encoding/csv"
	"encoding/json"
	"flag"
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

type Food struct {
	Name        string  `json:"name"`
	Calories    float64 `json:"calories"`
	Protein     float64 `json:"protein"`
	Fat         float64 `json:"fat"`
	Carbs       float64 `json:"carbs"`
	Description string  `json:"description"`
}

type MealPlanItem struct {
	ID       int     `json:"id"`
	FoodName string  `json:"foodName"`
	Portion  float64 `json:"portion"`
	Date     string  `json:"date"`
	MealType string  `json:"mealType"`
	Notes    string  `json:"notes"`
}

type Data struct {
	Foods  []Food         `json:"foods"`
	Plans  []MealPlanItem `json:"plans"`
	NextID int            `json:"next_id"`
}

const dataFile = "meal_data.json"

func loadData() *Data {
	var d Data
	file, err := os.ReadFile(dataFile)
	if err != nil {
		d.Foods = []Food{}
		d.Plans = []MealPlanItem{}
		d.NextID = 1
		return &d
	}
	err = json.Unmarshal(file, &d)
	if err != nil {
		d.Foods = []Food{}
		d.Plans = []MealPlanItem{}
		d.NextID = 1
	}
	return &d
}

func saveData(d *Data) {
	data, _ := json.MarshalIndent(d, "", "  ")
	os.WriteFile(dataFile, data, 0644)
}

func addFood(d *Data, name string, calories, protein, fat, carbs float64, description string) Food {
	f := Food{Name: name, Calories: calories, Protein: protein, Fat: fat, Carbs: carbs, Description: description}
	d.Foods = append(d.Foods, f)
	saveData(d)
	return f
}

func getFood(d *Data, name string) *Food {
	for i := range d.Foods {
		if strings.EqualFold(d.Foods[i].Name, name) {
			return &d.Foods[i]
		}
	}
	return nil
}

func suggestFoods(d *Data, prefix string) []string {
	var result []string
	for _, f := range d.Foods {
		if strings.HasPrefix(strings.ToLower(f.Name), strings.ToLower(prefix)) {
			result = append(result, f.Name)
		}
	}
	return result
}

func addMealPlan(d *Data, foodName string, portion float64, date, mealType, notes string) MealPlanItem {
	if date == "" {
		date = time.Now().Format("2006-01-02")
	}
	item := MealPlanItem{
		ID:       d.NextID,
		FoodName: foodName,
		Portion:  portion,
		Date:     date,
		MealType: mealType,
		Notes:    notes,
	}
	d.Plans = append(d.Plans, item)
	d.NextID++
	saveData(d)
	return item
}

func deletePlanItem(d *Data, id int) bool {
	for i, p := range d.Plans {
		if p.ID == id {
			d.Plans = append(d.Plans[:i], d.Plans[i+1:]...)
			saveData(d)
			return true
		}
	}
	return false
}

func getPlanForDate(d *Data, dateStr string) []MealPlanItem {
	var result []MealPlanItem
	for _, p := range d.Plans {
		if p.Date == dateStr {
			result = append(result, p)
		}
	}
	sortByMealType(result)
	return result
}

func sortByMealType(items []MealPlanItem) {
	order := map[string]int{"breakfast": 0, "lunch": 1, "dinner": 2, "snack": 3}
	for i := 0; i < len(items); i++ {
		for j := i + 1; j < len(items); j++ {
			if order[items[i].MealType] > order[items[j].MealType] {
				items[i], items[j] = items[j], items[i]
			}
		}
	}
}

func getDayStats(d *Data, dateStr string) (totalCal, totalProtein, totalFat, totalCarbs float64, details []map[string]interface{}) {
	items := getPlanForDate(d, dateStr)
	for _, p := range items {
		food := getFood(d, p.FoodName)
		if food != nil {
			factor := p.Portion
			totalCal += food.Calories * factor
			totalProtein += food.Protein * factor
			totalFat += food.Fat * factor
			totalCarbs += food.Carbs * factor
			details = append(details, map[string]interface{}{
				"mealType": p.MealType,
				"food":     food.Name,
				"portion":  p.Portion,
				"calories": food.Calories * factor,
			})
		} else {
			details = append(details, map[string]interface{}{
				"mealType": p.MealType,
				"food":     p.FoodName,
				"portion":  p.Portion,
				"calories": 0.0,
				"warning":  "Неизвестный продукт",
			})
		}
	}
	return
}

func generateRandomMenu(d *Data, dateStr string, caloriesLimit float64) []MealPlanItem {
	if len(d.Foods) == 0 {
		return nil
	}
	rand.Seed(time.Now().UnixNano())
	available := make([]Food, len(d.Foods))
	copy(available, d.Foods)
	rand.Shuffle(len(available), func(i, j int) { available[i], available[j] = available[j], available[i] })
	var selected []Food
	totalCal := 0.0
	mealTypes := []string{"breakfast", "lunch", "dinner", "snack"}
	idx := 0
	for _, food := range available {
		portionChoices := []float64{0.5, 0.75, 1.0, 1.25, 1.5}
		portion := portionChoices[rand.Intn(len(portionChoices))]
		cal := food.Calories * portion
		if totalCal+cal <= caloriesLimit {
			selected = append(selected, food)
			totalCal += cal
		}
		if totalCal >= caloriesLimit*0.8 {
			break
		}
	}
	var items []MealPlanItem
	for _, food := range selected {
		portion := []float64{0.5, 0.75, 1.0, 1.25, 1.5}[rand.Intn(5)]
		mtype := mealTypes[idx%len(mealTypes)]
		idx++
		item := MealPlanItem{
			ID:       d.NextID,
			FoodName: food.Name,
			Portion:  portion,
			Date:     dateStr,
			MealType: mtype,
			Notes:    "Сгенерировано автоматически",
		}
		d.Plans = append(d.Plans, item)
		d.NextID++
		items = append(items, item)
	}
	saveData(d)
	return items
}

func exportCSV(d *Data, filepath string, dateStr string) {
	file, err := os.Create(filepath)
	if err != nil {
		fmt.Println("Ошибка создания файла:", err)
		return
	}
	defer file.Close()
	writer := csv.NewWriter(file)
	defer writer.Flush()
	writer.Write([]string{"Date", "Meal Type", "Food", "Portion", "Calories", "Protein", "Fat", "Carbs"})
	var plans []MealPlanItem
	if dateStr != "" {
		plans = getPlanForDate(d, dateStr)
	} else {
		plans = d.Plans
	}
	for _, p := range plans {
		food := getFood(d, p.FoodName)
		c, pr, fa, ca := 0.0, 0.0, 0.0, 0.0
		if food != nil {
			c = food.Calories * p.Portion
			pr = food.Protein * p.Portion
			fa = food.Fat * p.Portion
			ca = food.Carbs * p.Portion
		}
		writer.Write([]string{
			p.Date,
			p.MealType,
			p.FoodName,
			fmt.Sprintf("%.1f", p.Portion),
			fmt.Sprintf("%.1f", c),
			fmt.Sprintf("%.1f", pr),
			fmt.Sprintf("%.1f", fa),
			fmt.Sprintf("%.1f", ca),
		})
	}
}

func importCSV(d *Data, filepath string) {
	file, err := os.Open(filepath)
	if err != nil {
		fmt.Println("Ошибка открытия файла:", err)
		return
	}
	defer file.Close()
	reader := csv.NewReader(file)
	records, _ := reader.ReadAll()
	if len(records) < 2 {
		return
	}
	header := records[0]
	for i := 1; i < len(records); i++ {
		row := records[i]
		if len(row) < 8 {
			continue
		}
		date := row[0]
		mealType := row[1]
		foodName := row[2]
		portion, _ := strconv.ParseFloat(row[3], 64)
		addMealPlan(d, foodName, portion, date, mealType, "Импортировано из CSV")
	}
}

func main() {
	var (
		cmd          string
		name         string
		calories     float64
		protein      float64
		fat          float64
		carbs        float64
		description  string
		foodName     string
		portion      float64
		date         string
		mealType     string
		notes        string
		id           int
		caloriesLimit float64
		output       string
		importFile   string
	)
	flag.StringVar(&cmd, "cmd", "", "Команда: add-food, add-meal, list, stats, delete, random, export, import")
	flag.StringVar(&name, "name", "", "Название продукта")
	flag.Float64Var(&calories, "calories", 0, "Калории")
	flag.Float64Var(&protein, "protein", 0, "Белки")
	flag.Float64Var(&fat, "fat", 0, "Жиры")
	flag.Float64Var(&carbs, "carbs", 0, "Углеводы")
	flag.StringVar(&description, "description", "", "Описание")
	flag.StringVar(&foodName, "food", "", "Название продукта для плана")
	flag.Float64Var(&portion, "portion", 1, "Количество порций")
	flag.StringVar(&date, "date", "", "Дата")
	flag.StringVar(&mealType, "meal-type", "lunch", "Тип приёма")
	flag.StringVar(&notes, "notes", "", "Заметки")
	flag.IntVar(&id, "id", 0, "ID пункта")
	flag.Float64Var(&caloriesLimit, "calories-limit", 2000, "Лимит калорий")
	flag.StringVar(&output, "output", "", "Файл для экспорта CSV")
	flag.StringVar(&importFile, "import", "", "Файл для импорта CSV")
	flag.Parse()

	data := loadData()

	switch cmd {
	case "add-food":
		if name == "" || calories == 0 {
			fmt.Println("Укажите --name и --calories")
			return
		}
		f := addFood(data, name, calories, protein, fat, carbs, description)
		fmt.Printf("✅ Продукт '%s' добавлен\n", f.Name)
	case "add-meal":
		if foodName == "" {
			fmt.Println("Укажите --food")
			return
		}
		if getFood(data, foodName) == nil {
			fmt.Println("Продукт не найден. Сначала добавьте его.")
			return
		}
		item := addMealPlan(data, foodName, portion, date, mealType, notes)
		fmt.Printf("✅ Приём пищи #%d добавлен\n", item.ID)
	case "list":
		d := date
		if d == "" {
			d = time.Now().Format("2006-01-02")
		}
		items := getPlanForDate(data, d)
		if len(items) == 0 {
			fmt.Printf("Нет записей на %s\n", d)
		} else {
			totalCal, totalProt, totalFat, totalCarbs, _ := getDayStats(data, d)
			fmt.Printf("📅 План на %s\n", d)
			fmt.Printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал")
			for _, p := range items {
				food := getFood(data, p.FoodName)
				cal := 0.0
				if food != nil {
					cal = food.Calories * p.Portion
				}
				fmt.Printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", p.ID, p.MealType, p.FoodName, p.Portion, cal)
			}
			fmt.Printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n", totalCal, totalProt, totalFat, totalCarbs)
		}
	case "stats":
		d := date
		if d == "" {
			d = time.Now().Format("2006-01-02")
		}
		totalCal, totalProt, totalFat, totalCarbs, details := getDayStats(data, d)
		fmt.Printf("📊 Статистика за %s\n", d)
		fmt.Printf("Калории: %.1f\n", totalCal)
		fmt.Printf("Белки: %.1f г\n", totalProt)
		fmt.Printf("Жиры: %.1f г\n", totalFat)
		fmt.Printf("Углеводы: %.1f г\n", totalCarbs)
		if len(details) > 0 {
			fmt.Println("Детали:")
			for _, det := range details {
				fmt.Printf("  %s: %s x%.1f = %.1f ккал\n", det["mealType"], det["food"], det["portion"], det["calories"])
			}
		}
	case "delete":
		if id == 0 {
			fmt.Println("Укажите --id")
			return
		}
		if deletePlanItem(data, id) {
			fmt.Printf("✅ Пункт #%d удалён\n", id)
		} else {
			fmt.Printf("❌ Пункт #%d не найден\n", id)
		}
	case "random":
		d := date
		if d == "" {
			d = time.Now().Format("2006-01-02")
		}
		items := generateRandomMenu(data, d, caloriesLimit)
		fmt.Printf("✅ Сгенерировано %d блюд на %s\n", len(items), d)
	case "export":
		if output == "" {
			fmt.Println("Укажите --output")
			return
		}
		exportCSV(data, output, date)
		fmt.Printf("Экспортировано в %s\n", output)
	case "import":
		if importFile == "" {
			fmt.Println("Укажите --import")
			return
		}
		importCSV(data, importFile)
		fmt.Printf("Импортировано из %s\n", importFile)
	default:
		interactiveMode(data)
	}
}

func interactiveMode(d *Data) {
	scanner := bufio.NewScanner(os.Stdin)
	for {
		fmt.Println("\n🍽️ Планировщик питания (интерактивный)")
		fmt.Println("1. Добавить продукт")
		fmt.Println("2. Добавить приём пищи")
		fmt.Println("3. Показать план на день")
		fmt.Println("4. Статистика за день")
		fmt.Println("5. Удалить пункт плана")
		fmt.Println("6. Сгенерировать случайное меню")
		fmt.Println("7. Экспорт CSV")
		fmt.Println("8. Импорт CSV")
		fmt.Println("0. Выход")
		fmt.Print("Выберите действие: ")
		scanner.Scan()
		choice := scanner.Text()
		switch choice {
		case "0":
			return
		case "1":
			fmt.Print("Название продукта: ")
			scanner.Scan()
			name := scanner.Text()
			if name == "" {
				fmt.Println("Название обязательно")
				continue
			}
			fmt.Print("Калории: ")
			scanner.Scan()
			cal, _ := strconv.ParseFloat(scanner.Text(), 64)
			fmt.Print("Белки (г): ")
			scanner.Scan()
			prot, _ := strconv.ParseFloat(scanner.Text(), 64)
			fmt.Print("Жиры (г): ")
			scanner.Scan()
			fat, _ := strconv.ParseFloat(scanner.Text(), 64)
			fmt.Print("Углеводы (г): ")
			scanner.Scan()
			carbs, _ := strconv.ParseFloat(scanner.Text(), 64)
			fmt.Print("Описание (необязательно): ")
			scanner.Scan()
			desc := scanner.Text()
			addFood(d, name, cal, prot, fat, carbs, desc)
			fmt.Printf("✅ Продукт '%s' добавлен\n", name)
		case "2":
			fmt.Print("Название продукта: ")
			scanner.Scan()
			foodName := scanner.Text()
			if foodName == "" {
				fmt.Println("Введите название")
				continue
			}
			if getFood(d, foodName) == nil {
				fmt.Println("Продукт не найден. Сначала добавьте его.")
				continue
			}
			fmt.Print("Количество порций (по умолчанию 1): ")
			scanner.Scan()
			portionStr := scanner.Text()
			portion := 1.0
			if portionStr != "" {
				portion, _ = strconv.ParseFloat(portionStr, 64)
			}
			fmt.Print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ")
			scanner.Scan()
			date := scanner.Text()
			if date == "" {
				date = time.Now().Format("2006-01-02")
			}
			fmt.Print("Тип приёма (breakfast/lunch/dinner/snack, по умолчанию lunch): ")
			scanner.Scan()
			mealType := scanner.Text()
			if mealType == "" {
				mealType = "lunch"
			}
			fmt.Print("Заметки: ")
			scanner.Scan()
			notes := scanner.Text()
			item := addMealPlan(d, foodName, portion, date, mealType, notes)
			fmt.Printf("✅ Добавлен приём пищи #%d\n", item.ID)
		case "3":
			fmt.Print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ")
			scanner.Scan()
			date := scanner.Text()
			if date == "" {
				date = time.Now().Format("2006-01-02")
			}
			items := getPlanForDate(d, date)
			if len(items) == 0 {
				fmt.Printf("Нет записей на %s\n", date)
			} else {
				totalCal, totalProt, totalFat, totalCarbs, _ := getDayStats(d, date)
				fmt.Printf("📅 План на %s\n", date)
				fmt.Printf("%-4s %-10s %-20s %-8s %-8s\n", "ID", "Приём", "Продукт", "Порция", "Ккал")
				for _, p := range items {
					food := getFood(d, p.FoodName)
					cal := 0.0
					if food != nil {
						cal = food.Calories * p.Portion
					}
					fmt.Printf("%-4d %-10s %-20s %-8.1f %-8.1f\n", p.ID, p.MealType, p.FoodName, p.Portion, cal)
				}
				fmt.Printf("Итого: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f\n", totalCal, totalProt, totalFat, totalCarbs)
			}
		case "4":
			fmt.Print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ")
			scanner.Scan()
			date := scanner.Text()
			if date == "" {
				date = time.Now().Format("2006-01-02")
			}
			totalCal, totalProt, totalFat, totalCarbs, details := getDayStats(d, date)
			fmt.Printf("📊 Статистика за %s\n", date)
			fmt.Printf("Калории: %.1f\n", totalCal)
			fmt.Printf("Белки: %.1f г\n", totalProt)
			fmt.Printf("Жиры: %.1f г\n", totalFat)
			fmt.Printf("Углеводы: %.1f г\n", totalCarbs)
			if len(details) > 0 {
				fmt.Println("Детали:")
				for _, det := range details {
					fmt.Printf("  %s: %s x%.1f = %.1f ккал\n", det["mealType"], det["food"], det["portion"], det["calories"])
				}
			}
		case "5":
			fmt.Print("ID пункта для удаления: ")
			scanner.Scan()
			idStr := scanner.Text()
			id, _ := strconv.Atoi(idStr)
			if deletePlanItem(d, id) {
				fmt.Println("✅ Удалено")
			} else {
				fmt.Println("❌ Не найдено")
			}
		case "6":
			fmt.Print("Дата (ГГГГ-ММ-ДД, Enter сегодня): ")
			scanner.Scan()
			date := scanner.Text()
			if date == "" {
				date = time.Now().Format("2006-01-02")
			}
			fmt.Print("Лимит калорий (по умолчанию 2000): ")
			scanner.Scan()
			limitStr := scanner.Text()
			limit := 2000.0
			if limitStr != "" {
				limit, _ = strconv.ParseFloat(limitStr, 64)
			}
			items := generateRandomMenu(d, date, limit)
			fmt.Printf("✅ Сгенерировано %d блюд\n", len(items))
		case "7":
			fmt.Print("Имя файла (CSV): ")
			scanner.Scan()
			file := scanner.Text()
			if file == "" {
				file = "meal_plan.csv"
			}
			fmt.Print("Дата (оставьте пустым для всех): ")
			scanner.Scan()
			date := scanner.Text()
			exportCSV(d, file, date)
			fmt.Printf("Экспортировано в %s\n", file)
		case "8":
			fmt.Print("Имя файла (CSV): ")
			scanner.Scan()
			file := scanner.Text()
			if file == "" {
				fmt.Println("Укажите файл")
				continue
			}
			importCSV(d, file)
			fmt.Printf("Импортировано из %s\n", file)
		default:
			fmt.Println("Неверный выбор")
		}
	}
}
