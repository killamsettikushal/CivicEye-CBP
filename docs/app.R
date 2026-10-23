# ============================================================
#  CivicEye — Civic Issue Analytics Dashboard (Shiny)
# ============================================================
#  Reads the same MySQL database as the Java desktop app and
#  turns the `issues` table into an interactive analytics view.
#
#  Run from the project root:
#      Rscript -e 'shiny::runApp("docs", port = 4321, launch.browser = TRUE)'
#  ...or open this file in RStudio and press "Run App".
#
#  Credentials, in order of precedence:
#    1. CIVICEYE_DB_USER / CIVICEYE_DB_PASSWORD / [CIVICEYE_DB_HOST,
#       CIVICEYE_DB_PORT, CIVICEYE_DB_NAME] environment variables
#    2. config.properties at the project root (db.user, db.password, db.url)
#  The file is read, never printed.
# ============================================================

library(shiny)
library(DBI)
library(RMariaDB)
library(ggplot2)
library(dplyr)
library(lubridate)

# ---------------- DATABASE CONNECTION ----------------

# Reads key=value pairs from config.properties, searching this working
# directory and up to two parents (RStudio opens docs/ as the root).
read_db_config <- function() {
  candidates <- c(
    file.path(getwd(), "config.properties"),
    file.path(getwd(), "..", "config.properties"),
    file.path(getwd(), "..", "..", "config.properties")
  )
  path <- candidates[file.exists(candidates)][1]
  if (is.na(path)) {
    return(list())
  }

  lines <- trimws(readLines(path, warn = FALSE))
  lines <- lines[nzchar(lines) & !startsWith(lines, "#")]
  parts <- strsplit(lines, "=", fixed = TRUE)

  keys <- vapply(parts, function(p) trimws(p[1]), character(1))
  vals <- vapply(parts, function(p) trimws(paste(p[-1], collapse = "=")), character(1))
  as.list(setNames(vals, keys))
}

# Pulls a setting from the environment first, then config.properties.
db_setting <- function(env_name, cfg_name, cfg, default = "") {
  value <- Sys.getenv(env_name, unset = "")
  if (nzchar(value)) {
    return(value)
  }
  if (!is.null(cfg[[cfg_name]]) && nzchar(cfg[[cfg_name]])) {
    return(cfg[[cfg_name]])
  }
  default
}

# jdbc:mysql://host:port/dbname?params  ->  host / port / dbname
parse_jdbc_url <- function(url) {
  match <- regmatches(url, regexec("^jdbc:mysql://([^:/]+)(?::([0-9]+))?/([^?]+)", url))[[1]]
  if (length(match) == 0) {
    return(list(host = "localhost", port = "3306", dbname = "civiceye_db"))
  }
  list(
    host = match[2],
    port = if (nzchar(match[3])) match[3] else "3306",
    dbname = match[4]
  )
}

cfg <- read_db_config()

db_user <- db_setting("CIVICEYE_DB_USER", "db.user", cfg)
db_password <- db_setting("CIVICEYE_DB_PASSWORD", "db.password", cfg)

jdbc <- parse_jdbc_url(
  db_setting("CIVICEYE_DB_URL", "db.url", cfg,
             "jdbc:mysql://localhost:3306/civiceye_db")
)

db_host <- db_setting("CIVICEYE_DB_HOST", "host", cfg, jdbc$host)
db_port <- as.integer(db_setting("CIVICEYE_DB_PORT", "port", cfg, jdbc$port))
db_name <- db_setting("CIVICEYE_DB_NAME", "dbname", cfg, jdbc$dbname)

if (!nzchar(db_user)) {
  stop(
    "No MySQL credentials found.\n",
    "  Set CIVICEYE_DB_USER and CIVICEYE_DB_PASSWORD, or add db.user / db.password ",
    "to config.properties at the project root.",
    call. = FALSE
  )
}

con <- tryCatch(
  dbConnect(
    RMariaDB::MariaDB(),
    host = db_host,
    port = db_port,
    dbname = db_name,
    username = db_user,
    password = db_password
  ),
  error = function(e) {
    stop(
      "Could not connect to MySQL at ", db_host, ":", db_port, "/", db_name,
      " as '", db_user, "'.\n  ", conditionMessage(e),
      call. = FALSE
    )
  }
)

issues_raw <- tryCatch(
  dbGetQuery(
    con,
    "SELECT issue_id, category, location, status, created_at FROM issues"
  ),
  error = function(e) {
    stop("Could not read the 'issues' table: ", conditionMessage(e), call. = FALSE)
  },
  finally = dbDisconnect(con)
)

required_cols <- c("issue_id", "category", "location", "status", "created_at")
if (!all(required_cols %in% names(issues_raw))) {
  stop("The issues table does not contain all required columns.", call. = FALSE)
}

# ---------------- DATA PREPARATION ----------------

status_levels <- c("Pending", "In Progress", "Resolved")
status_colors <- c(
  "Pending"     = "#D99A2B",
  "In Progress" = "#3D7BFF",
  "Resolved"    = "#2E9E68"
)
accent <- "#356AC3"

issues <- issues_raw %>%
  mutate(
    category = ifelse(is.na(category) | category == "", "Unknown", category),
    location = ifelse(is.na(location) | location == "", "Unknown", location),
    status   = factor(
      ifelse(is.na(status) | status == "", "Unknown", status),
      levels = c(status_levels, "Unknown")
    ),
    created_at = as.Date(created_at)
  ) %>%
  filter(!is.na(created_at)) %>%
  mutate(
    month   = floor_date(created_at, "month"),
    weekday = wday(created_at, label = TRUE, abbr = FALSE, week_start = 1)
  )

category_levels <- sort(unique(issues$category))
category_palette <- setNames(
  rep(
    c("#356AC3", "#278477", "#C9862A", "#8E5BD9", "#C0504D",
      "#2AA5B8", "#6B7F99", "#B35C9E"),
    length.out = length(category_levels)
  ),
  category_levels
)

date_min <- suppressWarnings(min(issues$created_at, na.rm = TRUE))
date_max <- suppressWarnings(max(issues$created_at, na.rm = TRUE))
if (!is.finite(date_min) || !is.finite(date_max)) {
  date_min <- Sys.Date() - 30
  date_max <- Sys.Date()
}

# First day of every month between two dates — keeps empty months visible.
# Both ends must be snapped to the month start: the issue rows carry real
# timestamps, and joining a raw date against month buckets silently misses.
month_span <- function(dates) {
  if (length(dates) == 0) {
    return(as.Date(character(0)))
  }
  seq(floor_date(min(dates), "month"),
      floor_date(max(dates), "month"),
      by = "month")
}

# Month labels as an ordered factor. Bars and heatmap tiles need a discrete
# axis: on a Date axis their width would be measured in days, not months.
month_labels <- function(months) {
  format(months, "%b %y")
}

# Show every label for short ranges, every other one when it gets busy.
month_breaks <- function(labels) {
  labels[seq(1, length(labels), by = if (length(labels) > 10) 2 else 1)]
}

# Shared look for every chart: light grid, muted axes, legend underneath.
theme_civic <- function(base_size = 12) {
  theme_minimal(base_size = base_size) +
    theme(
      plot.title = element_blank(),
      panel.grid.minor = element_blank(),
      panel.grid.major = element_line(colour = "#eef2f8", linewidth = 0.5),
      axis.title = element_text(colour = "#5b6b85", size = 10),
      axis.text = element_text(colour = "#46566f"),
      legend.position = "bottom",
      legend.title = element_text(colour = "#5b6b85", size = 10),
      legend.key.size = unit(0.9, "lines"),
      plot.margin = margin(6, 12, 4, 4)
    )
}

# Stacked fills always use the same colours and legend order.
scale_status_fill <- function() {
  scale_fill_manual(values = status_colors, drop = FALSE, name = NULL)
}

# ---------------- UI ----------------

ui <- fluidPage(
  theme = bslib::bs_theme(version = 5, bg = "#f5f7fb", fg = "#263445",
                          primary = accent),

  tags$head(
    tags$style(HTML("
      body {
        background: #f5f7fb;
        color: #263445;
        font-family: -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;
      }
      .container-fluid { max-width: 1360px; padding: 0 22px 26px; }

      /* ---------- header ---------- */
      .hero {
        background: linear-gradient(118deg, #0e1a31 0%, #1b2f5e 52%, #2c4f92 100%);
        color: #e9eefc;
        border-radius: 14px;
        padding: 22px 26px;
        margin: 18px 0 16px;
        display: flex;
        flex-wrap: wrap;
        gap: 18px;
        align-items: center;
        justify-content: space-between;
        box-shadow: 0 10px 24px rgba(16, 30, 60, 0.16);
      }
      .hero-left { display: flex; align-items: center; gap: 16px; }
      .hero-mark {
        width: 46px; height: 46px; border-radius: 11px;
        background: rgba(90, 150, 255, 0.18);
        border: 1px solid rgba(140, 180, 255, 0.45);
        color: #cfe0ff;
        font-weight: 700; font-size: 17px; letter-spacing: 0.5px;
        display: flex; align-items: center; justify-content: center;
      }
      .hero h1 {
        font-size: 25px; font-weight: 700; margin: 0 0 4px 0;
        color: #f3f7ff; letter-spacing: 0.2px;
      }
      .hero .tagline { color: #a9bde6; font-size: 13.5px; max-width: 560px; }
      .hero-stats { display: flex; gap: 26px; text-align: right; }
      .hero-stat-value { font-size: 21px; font-weight: 700; color: #ffffff; }
      .hero-stat-label {
        font-size: 11px; text-transform: uppercase; letter-spacing: 0.9px;
        color: #9db3df;
      }

      /* ---------- filter bar ---------- */
      .filter-panel {
        background: #ffffff;
        padding: 16px 20px 6px;
        border-radius: 12px;
        border: 1px solid #e3e9f3;
        box-shadow: 0 2px 8px rgba(28, 45, 80, 0.05);
        margin-bottom: 18px;
      }
      .filter-head {
        display: flex; justify-content: space-between; align-items: baseline;
        flex-wrap: wrap; gap: 8px;
      }
      .filter-head h4 {
        font-size: 13px; font-weight: 700; letter-spacing: 0.8px;
        text-transform: uppercase; color: #64748b; margin: 4px 0 10px 0;
      }
      .filter-count { font-size: 12.5px; color: #475569; }
      .filter-count strong { color: #1e3a6d; }

      /* ---------- tabs ---------- */
      .nav-tabs { border-bottom: 1px solid #e1e8f4; margin-bottom: 4px; }
      .nav-tabs > li > a,
      .nav-tabs .nav-link {
        border: none !important;
        background: transparent !important;
        color: #5b6b85 !important;
        font-weight: 600;
        font-size: 14px;
        padding: 10px 16px;
        border-bottom: 3px solid transparent !important;
        border-radius: 0;
      }
      .nav-tabs > li.active > a,
      .nav-tabs .nav-link.active {
        color: #17335f !important;
        border-bottom: 3px solid #356AC3 !important;
      }
      .tab-content { padding-top: 18px; }

      /* ---------- cards ---------- */
      .stat-card, .chart-card {
        background: #ffffff;
        border: 1px solid #e3e9f3;
        border-radius: 12px;
        padding: 18px 20px;
        box-shadow: 0 2px 8px rgba(28, 45, 80, 0.05);
      }
      .stat-card { margin-bottom: 18px; border-left: 4px solid #356AC3; }
      .stat-card.kpi-open    { border-left-color: #D99A2B; }
      .stat-card.kpi-progress{ border-left-color: #3D7BFF; }
      .stat-card.kpi-resolved{ border-left-color: #2E9E68; }

      .equal-row > [class*='col-'] { display: flex; }
      .equal-row .chart-card {
        width: 100%; display: flex; flex-direction: column;
        margin-bottom: 18px;
      }
      .equal-row .chart-card .shiny-plot-output { margin-top: auto; }

      .stat-label {
        color: #64748b; font-size: 12px; font-weight: 600;
        text-transform: uppercase; letter-spacing: 0.7px;
      }
      .stat-number {
        font-size: 30px; font-weight: 700; color: #17335f;
        margin: 6px 0 2px; line-height: 1.1;
      }
      .stat-hint { color: #7c8aa3; font-size: 12px; }

      .chart-title {
        font-size: 16.5px; font-weight: 700; color: #17335f;
        margin-bottom: 8px;
      }
      .chart-description {
        color: #5f6d85; font-size: 13px; line-height: 1.62;
        margin-bottom: 8px;
      }
      .chart-note {
        color: #23406f; font-size: 12.5px; line-height: 1.5;
        background: #f2f6fe; border-left: 3px solid #b9cdf3;
        border-radius: 4px; padding: 7px 10px; margin-bottom: 10px;
      }
      .chart-note:empty { display: none; }

      .section-title {
        font-size: 20px; font-weight: 700; color: #17335f;
        margin: 6px 0 16px 0;
      }
      .footer-note {
        color: #8a97ab; font-size: 12px; margin-top: 12px;
        border-top: 1px solid #e6ecf5; padding-top: 12px;
      }
      .btn-primary {
        background: #356AC3; border-color: #356AC3; font-size: 13px;
        font-weight: 600; padding: 6px 14px;
      }
      .btn-primary:hover { background: #27509b; border-color: #27509b; }

      @media (max-width: 700px) {
        .hero-stats { text-align: left; gap: 18px; }
        .hero h1 { font-size: 21px; }
      }
    "))
  ),

  # ---------- header ----------
  div(
    class = "hero",
    div(
      class = "hero-left",
      div(class = "hero-mark", "CE"),
      div(
        h1("CivicEye Analytics"),
        div(
          class = "tagline",
          "How civic reports arrive, where problems concentrate, and how fast they get resolved."
        )
      )
    ),
    div(
      class = "hero-stats",
      div(
        div(class = "hero-stat-value", textOutput("hero_total", inline = TRUE)),
        div(class = "hero-stat-label", "Issues in view")
      ),
      div(
        div(class = "hero-stat-value", textOutput("hero_categories", inline = TRUE)),
        div(class = "hero-stat-label", "Categories")
      )
    ),
  ),

  # ---------- filters ----------
  div(
    class = "filter-panel",
    div(
      class = "filter-head",
      h4("Filter issues"),
      div(class = "filter-count", textOutput("filter_note", inline = TRUE))
    ),
    fluidRow(
      column(
        3,
        selectInput(
          "category", "Category",
          choices = c("All", category_levels),
          selected = "All"
        )
      ),
      column(
        3,
        selectInput(
          "status", "Status",
          choices = c("All", status_levels),
          selected = "All"
        )
      ),
      column(
        4,
        dateRangeInput(
          "dates", "Reported between",
          start = date_min, end = date_max,
          min = date_min, max = date_max,
          format = "yyyy-mm-dd"
        )
      ),
      column(
        2,
        div(style = "margin-top: 25px;",
            actionButton("reset_filters", "Reset filters", class = "btn-primary"))
      )
    )
  ),

  # ---------- KPI cards ----------
  div(class = "row equal-row",
      column(3, div(class = "stat-card",
                    div(class = "stat-label", "Total issues"),
                    div(class = "stat-number", textOutput("total_issues", inline = TRUE)),
                    div(class = "stat-hint", "reports in the current selection"))),
      column(3, div(class = "stat-card kpi-open",
                    div(class = "stat-label", "Awaiting action"),
                    div(class = "stat-number", textOutput("pending_issues", inline = TRUE)),
                    div(class = "stat-hint", "still pending triage"))),
      column(3, div(class = "stat-card kpi-progress",
                    div(class = "stat-label", "In progress"),
                    div(class = "stat-number", textOutput("progress_issues", inline = TRUE)),
                    div(class = "stat-hint", "assigned and being worked on"))),
      column(3, div(class = "stat-card kpi-resolved",
                    div(class = "stat-label", "Resolved"),
                    div(class = "stat-number", textOutput("resolved_issues", inline = TRUE)),
                    div(class = "stat-hint", "closed by administrators")))
  ),

  tabsetPanel(
    id = "view",

    # =================== OVERVIEW ===================
    tabPanel(
      "Overview",
      div(class = "row equal-row",
          column(
            5,
            div(class = "chart-card",
                div(class = "chart-title", "Issues by Status"),
                div(
                  class = "chart-description",
                  paste0(
                    "Every filtered report is assigned to exactly one lifecycle state — ",
                    "Pending, In Progress or Resolved — and the ring shows how the whole ",
                    "selection splits across them. A wide amber band means a large share of ",
                    "reports has not been picked up yet, a wide blue band means work is ",
                    "under way, and a wide green band means closures are keeping pace. ",
                    "Treat it as the current backlog picture rather than a history: only the ",
                    "latest status of each report is stored, so a slice tells you where ",
                    "issues stand today, not how they travelled there."
                  )
                ),
                div(class = "chart-note", textOutput("note_status")),
                plotOutput("status_plot", height = "300px")
            )
          ),
          column(
            7,
            div(class = "chart-card",
                div(class = "chart-title", "Issues by Category"),
                div(
                  class = "chart-description",
                  paste0(
                    "Reports are grouped by category, counted, and sorted from most to ",
                    "least frequent, so the longest bar is the civic problem citizens ",
                    "raise most often. Compare bar lengths rather than exact values: one ",
                    "dominant bar means a single department owns most of the queue, while ",
                    "bars of similar length mean effort has to be spread across teams. ",
                    "Use the counts when planning staffing, and read the category ",
                    "alongside the status split to see whether that heavy workload is ",
                    "actually being cleared."
                  )
                ),
                div(class = "chart-note", textOutput("note_category")),
                plotOutput("category_plot", height = "300px")
            )
          )
      ),
      div(class = "row equal-row",
          column(
            12,
            div(class = "chart-card",
                div(class = "chart-title", "Monthly Issue Trend"),
                div(
                  class = "chart-description",
                  paste0(
                    "Each point counts the reports created in one calendar month and the ",
                    "line joins them in time order. A rising line means reporting is ",
                    "accelerating — either real civic activity or better awareness of the ",
                    "portal — while a flat line means steady demand; a drop at the right ",
                    "edge is usually an incomplete month rather than an improvement. Read ",
                    "the shape together with the volume of open issues to judge whether ",
                    "incoming reports are outpacing the team's capacity, and use the ",
                    "filters to compare categories or statuses against each other."
                  )
                ),
                div(class = "chart-note", textOutput("note_month")),
                plotOutput("monthly_plot", height = "300px")
            )
          )
      )
    ),

    # =================== CATEGORIES & LOCATIONS ===================
    tabPanel(
      "Categories & Locations",
      div(class = "row equal-row",
          column(
            7,
            div(class = "chart-card",
                div(class = "chart-title", "Category vs. Status"),
                div(
                  class = "chart-description",
                  paste0(
                    "Each horizontal bar is one category, split into coloured segments for ",
                    "Pending, In Progress and Resolved, so a single row shows both the ",
                    "size of the workload and how far it has progressed. Categories made ",
                    "mostly of amber carry the largest untouched backlog and are the best ",
                    "candidates for triage; categories that are mostly green are being ",
                    "closed reliably. The riskiest rows are long ones with a small green ",
                    "segment — a lot of reports, very few closures."
                  )
                ),
                plotOutput("category_status_plot", height = "320px")
            )
          ),
          column(
            5,
            div(class = "chart-card",
                div(class = "chart-title", "Top 10 Issue Locations"),
                div(
                  class = "chart-description",
                  paste0(
                    "Locations are counted, ranked and cut to the ten with the most ",
                    "reports, which shows where problems cluster geographically. A steep ",
                    "drop after the first few bars means a handful of spots deserve ",
                    "targeted attention, while a gentle slope means issues are spread ",
                    "evenly across the city. Use it to decide where a single field visit ",
                    "or inspection would reach the greatest number of reports, and cross-",
                    "check the winning spots in the heatmap below to see what kind of ",
                    "problem each one actually has."
                  )
                ),
                div(class = "chart-note", textOutput("note_location")),
                plotOutput("location_plot", height = "320px")
            )
          )
      ),
      div(class = "row equal-row",
          column(
            12,
            div(class = "chart-card",
                div(class = "chart-title", "Location vs. Category"),
                div(
                  class = "chart-description",
                  paste0(
                    "The ten busiest locations form the rows, categories form the columns, ",
                    "and each cell counts the reports belonging to that pair; darker cells ",
                    "with larger numbers mean more reports. Reading across a row tells you ",
                    "what kind of problem dominates a place — for example potholes at one ",
                    "junction — while reading down a column tells you where that category ",
                    "concentrates. A dark cell is a precise assignment: the field team ",
                    "knows both the site and the type of work to expect, and blank cells ",
                    "show which categories that location never reports."
                  )
                ),
                plotOutput("location_category_plot", height = "360px")
            )
          )
      )
    ),

    # =================== TIME TRENDS ===================
    tabPanel(
      "Time Trends",
      div(class = "row equal-row",
          column(
            6,
            div(class = "chart-card",
                div(class = "chart-title", "Monthly Status Mix"),
                div(
                  class = "chart-description",
                  paste0(
                    "Each column takes the reports created in one month and shows their ",
                    "current status as a share of that month's total, so months with very ",
                    "different volumes can still be compared. Recent months naturally lean ",
                    "amber because there has been little time to act on them; the ",
                    "diagnostic signal is an older month that is still mostly amber, which ",
                    "means that intake was never cleared. Read left to right to see whether ",
                    "the share of resolved reports in each cohort is growing over time."
                  )
                ),
                plotOutput("monthly_status_plot", height = "320px")
            )
          ),
          column(
            6,
            div(class = "chart-card",
                div(class = "chart-title", "Issues by Day of Week"),
                div(
                  class = "chart-description",
                  paste0(
                    "Reports are grouped by the weekday they were submitted, in calendar ",
                    "order from Monday to Sunday, with days that had no reports still shown ",
                    "as empty slots. Taller mid-week bars usually reflect office, school and ",
                    "commuter activity, while weekend reports tend to describe problems ",
                    "residents notice at home. Compare the two halves of the week to decide ",
                    "when intake and first-response staffing matter most, and remember that ",
                    "this measures when citizens report, not when staff act."
                  )
                ),
                plotOutput("weekday_plot", height = "320px")
            )
          )
      ),
      div(class = "row equal-row",
          column(
            12,
            div(class = "chart-card",
                div(class = "chart-title", "Category Activity by Month"),
                div(
                  class = "chart-description",
                  paste0(
                    "Rows are categories, columns are months, and each cell counts the ",
                    "reports of that category created in that month; the darker and larger ",
                    "the number, the more reports. Reading across a row shows whether a ",
                    "category is getting worse over time, while reading down a column shows ",
                    "which categories dominated that month. A single dark cell is a spike ",
                    "worth investigating, a row that darkens steadily is a slow-burning ",
                    "problem, and pale cells confirm months where a category was quiet."
                  )
                ),
                plotOutput("category_month_plot", height = "360px")
            )
          )
      )
    )
  ),

  div(
    class = "footer-note",
    paste0(
      "Source: CivicEye MySQL 'issues' table (issue_id, category, location, status, created_at). ",
      "Charts redraw as soon as a filter changes. The Java desktop app remains the place ",
      "where issues are filed and their status is updated."
    )
  )
)

# ---------------- SERVER LOGIC ----------------

server <- function(input, output, session) {

  filtered_issues <- reactive({
    df <- issues %>%
      filter(created_at >= input$dates[1], created_at <= input$dates[2])

    if (input$category != "All") {
      df <- df %>% filter(category == input$category)
    }

    if (input$status != "All") {
      df <- df %>% filter(status == input$status)
    }

    df
  })

  observeEvent(input$reset_filters, {
    updateSelectInput(session, "category", selected = "All")
    updateSelectInput(session, "status", selected = "All")
    updateDateRangeInput(session, "dates", start = date_min, end = date_max)
  }, ignoreInit = TRUE)

  # ---------- headline numbers ----------

  output$total_issues <- renderText({
    format(nrow(filtered_issues()), big.mark = ",")
  })

  output$pending_issues <- renderText({
    format(sum(filtered_issues()$status == "Pending"), big.mark = ",")
  })

  output$progress_issues <- renderText({
    format(sum(filtered_issues()$status == "In Progress"), big.mark = ",")
  })

  output$resolved_issues <- renderText({
    format(sum(filtered_issues()$status == "Resolved"), big.mark = ",")
  })

  output$hero_total <- renderText({
    format(nrow(filtered_issues()), big.mark = ",")
  })

  output$hero_categories <- renderText({
    n_distinct(filtered_issues()$category)
  })

  output$filter_note <- renderText({
    shown <- nrow(filtered_issues())
    total <- nrow(issues)
    paste0(
      "Showing ", format(shown, big.mark = ","), " of ",
      format(total, big.mark = ","), " issues",
      if (total > 0) paste0(" (", round(100 * shown / total), "%)") else ""
    )
  })

  # ---------- dynamic insight lines ----------

  output$note_status <- renderText({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match the current filters."))
    open <- sum(df$status %in% c("Pending", "In Progress"))
    paste0(
      round(100 * open / nrow(df)), "% of this selection is still open (",
      format(open, big.mark = ","), " issues); ",
      round(100 * sum(df$status == "Resolved") / nrow(df)),
      "% has been resolved."
    )
  })

  output$note_category <- renderText({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match the current filters."))
    top <- df %>% count(category, sort = TRUE)
    paste0(
      "Most reported: ", top$category[1], " with ",
      format(top$n[1], big.mark = ","), " issues (",
      round(100 * top$n[1] / nrow(df)), "% of the selection)."
    )
  })

  output$note_month <- renderText({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match the current filters."))
    counts <- df %>% count(month, name = "n") %>% arrange(month)
    validate(need(nrow(counts) > 1, "Only one month is in range — pick a wider date range to see a trend."))
    last <- tail(counts, 1)
    prev <- counts[nrow(counts) - 1, ]
    change <- last$n - prev$n
    direction <- if (change > 0) "up" else if (change < 0) "down" else "flat"
    paste0(
      "Latest month (", format(last$month, "%b %Y"), "): ",
      format(last$n, big.mark = ","), " reports, ", direction,
      if (change != 0) paste0(" ", abs(change), " versus ", format(prev$month, "%b %Y")) else "",
      ". Peak: ", format(counts$n[which.max(counts$n)], big.mark = ","),
      " in ", format(counts$month[which.max(counts$n)], "%b %Y"), "."
    )
  })

  output$note_location <- renderText({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match the current filters."))
    top <- df %>% count(location, sort = TRUE)
    paste0(
      "Busiest location: ", top$location[1], " (",
      format(top$n[1], big.mark = ","), " reports, ",
      round(100 * top$n[1] / nrow(df)), "% of the selection)."
    )
  })

  # ---------- charts ----------

  output$category_plot <- renderPlot({
    df <- filtered_issues() %>% count(category, sort = TRUE)
    validate(need(nrow(df) > 0, "No issues match these filters."))

    ggplot(df, aes(x = n, y = reorder(category, n))) +
      geom_col(fill = accent, width = 0.68) +
      geom_text(aes(label = n), hjust = -0.22, size = 3.4, colour = "#2b3b55") +
      scale_x_continuous(expand = expansion(mult = c(0, 0.2))) +
      labs(x = NULL, y = NULL) +
      theme_civic()
  })

  output$status_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(status) %>%
      filter(n > 0)

    validate(need(nrow(df) > 0, "No issues match these filters."))

    ggplot(df, aes(x = 2, y = n, fill = status)) +
      geom_col(width = 1, colour = "white", linewidth = 0.7) +
      geom_text(
        aes(label = scales::percent(n / sum(n), accuracy = 1)),
        position = position_stack(vjust = 0.5),
        colour = "white", fontface = "bold", size = 3.6
      ) +
      coord_polar(theta = "y") +
      xlim(0.4, 2.6) +
      scale_status_fill() +
      theme_void(base_size = 12) +
      theme(
        legend.position = "bottom",
        legend.title = element_blank(),
        plot.margin = margin(0, 0, 0, 0)
      )
  })

  output$monthly_plot <- renderPlot({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match these filters."))

    months <- month_span(df$created_at)
    labels <- month_labels(months)

    counts <- df %>%
      mutate(month_lab = factor(format(month, "%b %y"), levels = labels)) %>%
      count(month_lab, name = "n") %>%
      right_join(data.frame(month_lab = factor(labels, levels = labels)),
                 by = "month_lab") %>%
      mutate(n = ifelse(is.na(n), 0, n))

    # Columns keep a single month readable; the line and points carry the
    # trend once there is more than one month in range.
    # A lone month gets a slim column so it reads as a bar, not a slab.
    bar_width <- if (length(months) == 1) 0.28 else 0.7

    ggplot(counts, aes(x = month_lab, y = n, group = 1)) +
      geom_col(fill = "#cfdcf6", width = bar_width) +
      (if (length(months) > 1) geom_line(colour = accent, linewidth = 1) else NULL) +
      geom_point(colour = accent, size = 2.4) +
      (if (length(months) <= 12) {
        geom_text(aes(label = n), vjust = -0.7, size = 3.2, colour = "#2b3b55")
      } else {
        NULL
      }) +
      scale_x_discrete(breaks = month_breaks(labels),
                       expand = expansion(add = 0.6)) +
      scale_y_continuous(expand = expansion(mult = c(0, 0.16))) +
      labs(x = NULL, y = "Reports per month") +
      theme_civic() +
      theme(axis.text.x = element_text(angle = 35, hjust = 1))
  })

  output$category_status_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(category, status) %>%
      filter(n > 0)

    validate(need(nrow(df) > 0, "No issues match these filters."))

    ggplot(df, aes(x = n, y = reorder(category, n, sum), fill = status)) +
      geom_col(width = 0.66) +
      scale_x_continuous(expand = expansion(mult = c(0, 0.04))) +
      scale_status_fill() +
      labs(x = "Number of issues", y = NULL) +
      theme_civic()
  })

  output$location_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(location, sort = TRUE) %>%
      slice_head(n = 10)

    validate(need(nrow(df) > 0, "No issues match these filters."))

    ggplot(df, aes(x = n, y = reorder(location, n))) +
      geom_col(fill = "#278477", width = 0.68) +
      geom_text(aes(label = n), hjust = -0.22, size = 3.2, colour = "#2b3b55") +
      scale_x_continuous(expand = expansion(mult = c(0, 0.2))) +
      labs(x = NULL, y = NULL) +
      theme_civic()
  })

  output$weekday_plot <- renderPlot({
    df <- filtered_issues() %>%
      count(weekday, .drop = FALSE)

    validate(need(sum(df$n) > 0, "No issues match these filters."))

    ggplot(df, aes(x = weekday, y = n)) +
      geom_col(fill = "#D99A2B", width = 0.66) +
      geom_text(aes(label = ifelse(n > 0, n, "")), vjust = -0.4,
                size = 3.2, colour = "#2b3b55") +
      scale_y_continuous(expand = expansion(mult = c(0, 0.12))) +
      labs(x = NULL, y = "Reports submitted") +
      theme_civic() +
      theme(axis.text.x = element_text(angle = 30, hjust = 1))
  })

  output$monthly_status_plot <- renderPlot({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match these filters."))

    months <- month_span(df$created_at)
    labels <- month_labels(months)

    # Keep month_lab a factor with chronological levels: joining a character
    # column would drop the order and ggplot would sort the months alphabetically.
    grid <- data.frame(
      month_lab = factor(rep(labels, each = length(status_levels)), levels = labels),
      status = factor(rep(status_levels, times = length(labels)),
                      levels = status_levels)
    )

    counts <- df %>%
      mutate(month_lab = factor(format(month, "%b %y"), levels = labels)) %>%
      count(month_lab, status) %>%
      right_join(grid, by = c("month_lab", "status")) %>%
      mutate(n = ifelse(is.na(n), 0, n))

    bar_width <- if (length(months) == 1) 0.32 else 0.78

    ggplot(counts, aes(x = month_lab, y = n, fill = status)) +
      geom_col(width = bar_width, position = "fill") +
      scale_y_continuous(labels = scales::percent, expand = c(0, 0)) +
      scale_x_discrete(breaks = month_breaks(labels),
                       expand = expansion(add = 0.55)) +
      scale_status_fill() +
      labs(x = NULL, y = "Share of each month's reports") +
      theme_civic() +
      theme(axis.text.x = element_text(angle = 35, hjust = 1))
  })

  output$category_month_plot <- renderPlot({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match these filters."))

    months <- month_span(df$created_at)
    labels <- month_labels(months)

    greys <- expand.grid(
      month_lab = factor(labels, levels = labels),
      category = category_levels,
      stringsAsFactors = FALSE
    )

    counts <- df %>%
      mutate(month_lab = factor(format(month, "%b %y"), levels = labels)) %>%
      count(month_lab, category) %>%
      right_join(greys, by = c("month_lab", "category")) %>%
      mutate(n = ifelse(is.na(n), 0, n))

    peak <- max(counts$n)

    ggplot(counts, aes(x = month_lab, y = reorder(category, n, sum), fill = n)) +
      geom_tile(colour = "white", linewidth = 0.6) +
      geom_text(
        aes(label = ifelse(n > 0, n, ""),
            colour = n >= peak * 0.55),
        size = 3.1
      ) +
      scale_colour_manual(values = c(`TRUE` = "white", `FALSE` = "#22314a"),
                          guide = "none") +
      scale_fill_gradient(low = "#eef3fc", high = "#2f5fb3", name = "Issues") +
      scale_x_discrete(breaks = month_breaks(labels), expand = c(0, 0)) +
      labs(x = NULL, y = NULL) +
      theme_civic() +
      theme(
        axis.text.x = element_text(angle = 35, hjust = 1),
        panel.grid.major = element_blank(),
        legend.position = "bottom",
        legend.key.width = unit(1.6, "lines")
      )
  })

  output$location_category_plot <- renderPlot({
    df <- filtered_issues()
    validate(need(nrow(df) > 0, "No issues match these filters."))

    top_locations <- df %>%
      count(location, sort = TRUE) %>%
      slice_head(n = 10) %>%
      pull(location)

    # Complete the grid so every location/category pair gets a cell: missing
    # combinations are real information (that place never reported that kind).
    greys <- expand.grid(
      location = top_locations,
      category = category_levels,
      stringsAsFactors = FALSE
    )

    counts <- df %>%
      filter(location %in% top_locations) %>%
      count(location, category) %>%
      right_join(greys, by = c("location", "category")) %>%
      mutate(n = ifelse(is.na(n), 0, n))

    peak <- max(counts$n)

    ggplot(counts, aes(x = reorder(category, n, sum),
                       y = reorder(location, n, sum), fill = n)) +
      geom_tile(colour = "white", linewidth = 0.6) +
      geom_text(
        aes(label = ifelse(n > 0, n, ""),
            colour = n >= peak * 0.55),
        size = 3.2, fontface = "bold"
      ) +
      scale_colour_manual(values = c(`TRUE` = "white", `FALSE` = "#22314a"),
                          guide = "none") +
      scale_fill_gradient(low = "#eef3fc", high = "#2f5fb3", name = "Issues") +
      labs(x = NULL, y = NULL) +
      theme_civic() +
      theme(
        axis.text.x = element_text(angle = 28, hjust = 1),
        panel.grid.major = element_blank(),
        legend.key.width = unit(1.6, "lines")
      )
  })
}

shinyApp(ui = ui, server = server)
