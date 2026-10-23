
library(shiny)
library(DBI)
library(RMariaDB)
library(ggplot2)
library(dplyr)
library(lubridate)

# ---------------- DATABASE CONNECTION ----------------

db_user <- Sys.getenv("CIVICEYE_DB_USER")
db_password <- Sys.getenv("CIVICEYE_DB_PASSWORD")

if (db_user == "" || db_password == "") {
  stop("Set CIVICEYE_DB_USER and CIVICEYE_DB_PASSWORD before running the app.")
}

con <- dbConnect(
  RMariaDB::MariaDB(),
  host = Sys.getenv("CIVICEYE_DB_HOST", "localhost"),
  port = as.integer(Sys.getenv("CIVICEYE_DB_PORT", "3306")),
  dbname = "civiceye_db",
  username = db_user,
  password = db_password
)

issues <- tryCatch(
  dbGetQuery(
    con,
    "SELECT issue_id, category, location, status, created_at FROM issues"
  ),
  finally = dbDisconnect(con)
)

required_cols <- c("issue_id", "category", "location", "status", "created_at")
if (!all(required_cols %in% names(issues))) {
  stop("The issues table does not contain all required columns.")
}

issues <- issues %>%
  mutate(
    category = ifelse(is.na(category) | category == "", "Unknown", category),
    location = ifelse(is.na(location) | location == "", "Unknown", location),
    status = ifelse(is.na(status) | status == "", "Unknown", status),
    created_at = as.Date(created_at)
  ) %>%
  filter(!is.na(created_at)) %>%
  mutate(
    month = floor_date(created_at, "month"),
    weekday = weekdays(created_at)
  )

# ---------------- SIMPLE UI ----------------

ui <- fluidPage(
  tags$head(
    tags$style(HTML("
      body {
        background: #f5f7fb;
        color: #263445;
        font-family: Arial, sans-serif;
      }
      .main-title {
        font-size: 28px;
        font-weight: 700;
        margin-top: 15px;
        margin-bottom: 5px;
      }
      .subtitle {
        color: #667085;
        margin-bottom: 22px;
      }
      .filter-panel {
        background: white;
        padding: 18px;
        border-radius: 10px;
        border: 1px solid #e5eaf1;
        margin-bottom: 18px;
      }
      .stat-card, .chart-card {
        background: white;
        border: 1px solid #e5eaf1;
        border-radius: 10px;
        padding: 18px;
        margin-bottom: 18px;
      }
      .stat-label {
        color: #667085;
        font-size: 14px;
      }
      .stat-number {
        font-size: 28px;
        font-weight: 700;
        margin-top: 7px;
      }
      .chart-title {
        font-size: 18px;
        font-weight: 700;
        margin-bottom: 5px;
      }
      .chart-description {
        color: #667085;
        font-size: 13px;
        line-height: 1.5;
        min-height: 40px;
        margin-bottom: 10px;
      }
      .section-title {
        font-size: 21px;
        font-weight: 700;
        margin: 8px 0 15px 0;
      }
      .btn-primary {
        background: #2457c5;
        border-color: #2457c5;
      }
    "))
  ),
  
  fluidRow(
    column(
      12,
      div(class = "main-title", "CivicEye Dashboard"),
      div(
        class = "subtitle",
        "Monitor civic issues, reporting patterns, and issue status."
      )
    )
  ),
  
  fluidRow(
    column(
      12,
      div(
        class = "filter-panel",
        h4("Filter issues"),
        fluidRow(
          column(
            4,
            selectInput(
              "category", "Category",
              choices = c("All", sort(unique(issues$category))),
              selected = "All"
            )
          ),
          column(
            4,
            selectInput(
              "status", "Status",
              choices = c("All", sort(unique(issues$status))),
              selected = "All"
            )
          ),
          column(
            4,
            dateRangeInput(
              "dates", "Reported between",
              start = min(issues$created_at),
              end = max(issues$created_at),
              min = min(issues$created_at),
              max = max(issues$created_at),
              format = "yyyy-mm-dd"
            )
          )
        )
      )
    )
  ),
  
  div(class = "section-title", "Issue Overview"),
  
  fluidRow(
    column(3, div(class = "stat-card",
                  div(class = "stat-label", "Total Issues"),
                  textOutput("total_issues", inline = TRUE)
    )),
    column(3, div(class = "stat-card",
                  div(class = "stat-label", "Open Issues"),
                  textOutput("open_issues", inline = TRUE)
    )),
    column(3, div(class = "stat-card",
                  div(class = "stat-label", "Resolved Issues"),
                  textOutput("resolved_issues", inline = TRUE)
    )),
    column(3, div(class = "stat-card",
                  div(class = "stat-label", "Issue Categories"),
                  textOutput("total_categories", inline = TRUE)
    ))
  ),
  
  div(class = "section-title", "Issue Analysis"),
  
  fluidRow(
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Issues by Category"),
                  div(class = "chart-description",
                      "Counts reported issues in each category to help identify the most common civic problems."
                  ),
                  plotOutput("category_plot", height = "280px")
    )),
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Issues by Status"),
                  div(class = "chart-description",
                      "Shows how issues are distributed across statuses, giving an overview of current progress."
                  ),
                  plotOutput("status_plot", height = "280px")
    ))
  ),
  
  fluidRow(
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Monthly Issue Trend"),
                  div(class = "chart-description",
                      "Tracks the number of reports submitted each month to reveal changes in reporting over time."
                  ),
                  plotOutput("monthly_plot", height = "280px")
    )),
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Category vs. Status"),
                  div(class = "chart-description",
                      "Compares statuses within each category to help identify categories with more unresolved issues."
                  ),
                  plotOutput("category_status_plot", height = "280px")
    ))
  ),
  
  fluidRow(
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Top 10 Issue Locations"),
                  div(class = "chart-description",
                      "Displays the ten locations with the most reports, helping teams see where issues are concentrated."
                  ),
                  plotOutput("location_plot", height = "320px")
    )),
    column(6, div(class = "chart-card",
                  div(class = "chart-title", "Issues by Day of Week"),
                  div(class = "chart-description",
                      "Shows the number of reports created on each weekday to reveal reporting patterns."
                  ),
                  plotOutput("weekday_plot", height = "320px")
    ))
  ),
  
  div(
    class = "subtitle",
    "Data source: CivicEye MySQL issues table. Charts reflect the selected filters."
  )
)

# ---------------- SERVER LOGIC ----------------

server <- function(input, output, session) {
  
  filtered_issues <- reactive({
    df <- issues
    
    if (input$category != "All") {
      df <- df %>% filter(category == input$category)
    }
    
    if (input$status != "All") {
      df <- df %>% filter(status == input$status)
    }
    
    if (!is.null(input$dates) && length(input$dates) == 2 &&
        all(!is.na(input$dates))) {
      df <- df %>%
        filter(created_at >= input$dates[1],
               created_at <= input$dates[2])
    }
    
    df
  })
  
  output$total_issues <- renderText({
    nrow(filtered_issues())
  })
  
  output$open_issues <- renderText({
    sum(tolower(filtered_issues()$status) == "open", na.rm = TRUE)
  })
  
  output$resolved_issues <- renderText({
    sum(tolower(filtered_issues()$status) == "resolved", na.rm = TRUE)
  })
  
  output$total_categories <- renderText({
    n_distinct(filtered_issues()$category)
  })
  
  output$category_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(category, sort = TRUE)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = reorder(category, n), y = n)) +
      geom_col(fill = "#356AC3", width = 0.7) +
      coord_flip() +
      labs(x = NULL, y = "Number of issues") +
      theme_minimal(base_size = 12) +
      theme(panel.grid.minor = element_blank())
  })
  
  output$status_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(status)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = "", y = n, fill = status)) +
      geom_col(width = 1, color = "white") +
      coord_polar(theta = "y") +
      labs(x = NULL, y = NULL, fill = "Status") +
      theme_void(base_size = 12)
  })
  
  output$monthly_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(month, name = "total") %>%
      arrange(month)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = month, y = total)) +
      geom_line(color = "#356AC3", linewidth = 1) +
      geom_point(color = "#356AC3", size = 2.5) +
      labs(x = "Month", y = "Number of issues") +
      scale_x_date(date_labels = "%b %Y", date_breaks = "1 month") +
      theme_minimal(base_size = 12) +
      theme(
        axis.text.x = element_text(angle = 35, hjust = 1),
        panel.grid.minor = element_blank()
      )
  })
  
  output$category_status_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(category, status)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = category, y = n, fill = status)) +
      geom_col() +
      labs(x = NULL, y = "Number of issues", fill = "Status") +
      theme_minimal(base_size = 12) +
      theme(
        axis.text.x = element_text(angle = 35, hjust = 1),
        panel.grid.minor = element_blank()
      )
  })
  
  output$location_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(location, sort = TRUE) %>%
      slice_head(n = 10)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = reorder(location, n), y = n)) +
      geom_col(fill = "#278477", width = 0.7) +
      coord_flip() +
      labs(x = NULL, y = "Number of issues") +
      theme_minimal(base_size = 12) +
      theme(panel.grid.minor = element_blank())
  })
  
  output$weekday_plot <- renderPlot({
    day_order <- c(
      "Monday", "Tuesday", "Wednesday", "Thursday",
      "Friday", "Saturday", "Sunday"
    )
    
    df <- filtered_issues() %>%
      count(weekday) %>%
      mutate(
        weekday = factor(weekday, levels = day_order)
      ) %>%
      arrange(weekday)
    
    validate(need(nrow(df) > 0, "No issues match these filters."))
    
    ggplot(df, aes(x = weekday, y = n)) +
      geom_col(fill = "#E3A23B", width = 0.7) +
      labs(x = NULL, y = "Number of issues") +
      theme_minimal(base_size = 12) +
      theme(
        axis.text.x = element_text(angle = 30, hjust = 1),
        panel.grid.minor = element_blank()
      )
  })
}

shinyApp(ui = ui, server = server)
