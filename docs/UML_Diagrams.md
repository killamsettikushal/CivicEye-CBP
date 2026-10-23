# CivicEye — UML Diagrams

The nine UML diagrams required by the *UNIT III (Part-2)* presentation (use case, class, object, sequence, collaboration, state machine, activity, component, deployment), modelled against the CivicEye implementation in `src/civiceye/**`.

All nine use **StarUML notation**: white shapes, 1 px black borders, black Arial labels, stick-figure actors, underlined object rectangles, thick fork/join bars and 3-D nodes — no colour fills, no icons and no diagram-frame legends.

Each diagram is stored three times in [`uml/`](uml/): the PlantUML source (`NN_name.puml`), the rendered PNG (`NN_name.png`, 2×) and the vector SVG (`NN_name.svg`). Re-render them with `cd docs/uml && ./render.sh`.

> An earlier, text-only Mermaid rendering of the same nine diagrams is kept in [`uml/mermaid/`](uml/mermaid/) for reference; the PlantUML set is the canonical one because it uses proper UML notation.

---

## 1. Use Case Diagram

![Use Case Diagram](uml/01_use_case.png)

Shows the actors of CivicEye, the system boundary and the way the individual
use cases are related to each other.

- **Actors** — `Citizen` and `Administrator`, drawn as StarUML stick figures
  outside the system boundary. `Login` and `Logout` are shared by both.
- **System boundary** — the rectangle around the use cases is the Swing
  application `CivicEye`.
- **`«include»`** — behaviour that is always executed: *Register Account* →
  *Validate Registration Form*, *Report New Issue* → *Validate Issue Form*,
  *Login* → *Verify Credentials*, *Update Issue Status* → *Validate Status
  Transition*.
- Every association is a single straight line between the actor and the
  ellipse: the use cases are ordered so that the Citizen cases sit at the top
  and the Administrator cases at the bottom, which keeps the lines short and
  free of crossings.

<details><summary>PlantUML source — <code>uml/01_use_case.puml</code></summary>

```plantuml
@startuml civiceye_use_case
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 1 of 9 : USE CASE DIAGRAM
'  Notation: StarUML 5 — stick-figure actors, ellipses, straight
'  association lines, dashed «include» arrows.
' ===================================================================
title CivicEye — Use Case Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 12
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam BorderColor #000000
skinparam BorderThickness 1

skinparam actor {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 12
}
skinparam usecase {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 12
}
skinparam rectangle {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontStyle bold
  FontSize 13
}

left to right direction
skinparam nodesep 40
skinparam ranksep 85

actor "Citizen" as C
actor "Administrator" as A

rectangle "CivicEye\nCivic Issue Reporting & Management System" {

  usecase "Register Account"             as UC_REG
  usecase "Report New Issue"             as UC_REPORT
  usecase "View My Reported Issues"      as UC_MY
  usecase "Login"                        as UC_LOGIN
  usecase "Logout"                       as UC_LOGOUT
  usecase "View All Issues"              as UC_ALL
  usecase "Filter Issues by Status"      as UC_FILTER
  usecase "Update Issue Status"          as UC_UPDATE

  usecase "Validate Registration Form"   as UC_VREG
  usecase "Validate Issue Form"          as UC_VISSUE
  usecase "Verify Credentials"           as UC_VERIFY
  usecase "Validate Status Transition"   as UC_VTRANS
}

' --- actor / use case associations (straight lines) -------------------
C -- UC_REG
C -- UC_REPORT
C -- UC_MY
C -- UC_LOGIN
C -- UC_LOGOUT

A -- UC_LOGIN
A -- UC_LOGOUT
A -- UC_ALL
A -- UC_FILTER
A -- UC_UPDATE

' --- include : behaviour that is always executed ----------------------
UC_REG    ..> UC_VREG   : <<include>>
UC_REPORT ..> UC_VISSUE : <<include>>
UC_LOGIN  ..> UC_VERIFY : <<include>>
UC_UPDATE ..> UC_VTRANS : <<include>>

@enduml
```

</details>

---

## 2. Class Diagram

![Class Diagram](uml/02_class.png)

The static model of the application as plain class rectangles — no package
frames, exactly as StarUML draws a class diagram. The UI classes hold no SQL
and no business rules, the service classes hold the rules, and only the DAO
classes issue SQL.

- `User` is abstract; `Citizen` and `Admin` specialise it and override `getRole()`.
- `Issue` is a plain POJO (7 attributes, accessors plus `setIssueId()` and
  `setStatus()`).
- `IssueService.updateStatus()` takes the *requesting user* and rejects the call
  unless it is an `Admin`, so no view can bypass the rule.
- `AuthService` depends on `PasswordUtils` (SHA-256 + per-user salt) and on
  `ValidationService`; both DAOs obtain their connection from `DBConnection`.
- The UI classes are composed of the two panels (`IssueReportPanel`,
  `IssueTablePanel`) and both dashboards.

<details><summary>PlantUML source — <code>uml/02_class.puml</code></summary>

```plantuml
@startuml civiceye_class
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 2 of 9 : CLASS DIAGRAM
'  Notation: StarUML 5 — plain class rectangles (no package frames),
'  white fill, black border, Arial, attribute / operation
'  compartments.  Classes of the same layer are kept together.
' ===================================================================
title CivicEye — Class Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam ArrowFontSize 10
skinparam BorderColor #000000
skinparam BorderThickness 1
skinparam classAttributeIconSize 0
skinparam nodesep 35
skinparam ranksep 55

skinparam class {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  FontStyle bold
  AttributeFontColor #000000
  AttributeFontSize 10
  AttributeFontStyle normal
  OperationFontColor #000000
  OperationFontSize 10
  OperationFontStyle normal
}

hide circle

' ===================================================================
'  model
' ===================================================================
abstract class User {
  -userId : int
  -name : String
  -email : String
  +getUserId() : int
  +getName() : String
  +getEmail() : String
  +getRole() : String
}

class Citizen {
  +getRole() : String
  +toString() : String
}

class Admin {
  +getRole() : String
  +displayAdminMessage() : void
}

class Issue {
  -issueId : int
  -reporterId : int
  -category : String
  -location : String
  -description : String
  -status : String
  -createdAt : LocalDateTime
  +getIssueId() : int
  +getReporterId() : int
  +getStatus() : String
  +setStatus(status : String) : void
  +getFormattedCreatedAt() : String
  +toString() : String
}

' ===================================================================
'  service
' ===================================================================
class AuthService {
  -userDAO : UserDAO
  -validationService : ValidationService
  +login(email : String, password : String) : User
  +registerCitizen(name : String, email : String, password : String) : String
}

class ValidationService {
  +validateEmail(email : String) : boolean
  +validateLoginForm(email : String, password : String) : String
  +validateRegistrationForm(name : String, email : String, password : String) : String
  +validateIssueForm(category : String, location : String, description : String) : String
  +validateStatusTransition(currentStatus : String, newStatus : String) : boolean
}

class IssueService {
  -issueDAO : IssueDAO
  -validationService : ValidationService
  +submitIssue(reporterId : int, category : String, location : String, description : String) : Issue
  +getIssuesByReporter(reporterId : int) : List<Issue>
  +getAllIssues() : List<Issue>
  +updateStatus(issueId : int, newStatus : String, requestingUser : User) : String
}

' ===================================================================
'  dao
' ===================================================================
class UserDAO {
  +findByEmail(email : String) : UserRecord
  +createUser(name : String, email : String, passwordHash : String, salt : String, role : String) : int
  +emailExists(email : String) : boolean
}

class IssueDAO {
  +createIssue(issue : Issue) : boolean
  +getIssuesByReporter(reporterId : int) : List<Issue>
  +getAllIssues() : List<Issue>
  +getIssueById(issueId : int) : Issue
  +updateIssueStatus(issueId : int, newStatus : String) : boolean
  +deleteIssue(issueId : int) : boolean
}

class UserRecord {
  +userId : int
  +name : String
  +email : String
  +passwordHash : String
  +salt : String
  +role : String
}

' ===================================================================
'  database + util
' ===================================================================
class DBConnection {
  -{static} DB_URL : String
  -{static} DB_USER : String
  -{static} DB_PASSWORD : String
  +{static} getConnection() : Connection
  +{static} testConnection() : boolean
  +{static} getDbUrl() : String
}

class PasswordUtils {
  +{static} generateSalt() : String
  +{static} hashPassword(password : String, salt : String) : String
  +{static} verifyPassword(plaintext : String, storedHash : String, salt : String) : boolean
}

class AppConstants {
  +{static} APP_NAME : String
  +{static} ROLE_CITIZEN : String
  +{static} ROLE_ADMIN : String
  +{static} STATUS_PENDING : String
  +{static} STATUS_IN_PROGRESS : String
  +{static} STATUS_RESOLVED : String
  +{static} ISSUE_CATEGORIES : String[]
}

' ===================================================================
'  ui
' ===================================================================
class LoginFrame {
  -emailField : JTextField
  -passwordField : JPasswordField
  -authService : AuthService
  +actionPerformed(e : ActionEvent) : void
}

class RegistrationDialog {
  -nameField : JTextField
  -emailField : JTextField
  -passwordField : JPasswordField
  +actionPerformed(e : ActionEvent) : void
}

class CitizenDashboard {
  -citizen : Citizen
  -issueService : IssueService
  -refreshTable() : void
  -logout() : void
}

class IssueReportPanel {
  -categoryBox : JComboBox
  -locationField : JTextField
  -descArea : JTextArea
  -attemptSubmit() : void
  -clearForm() : void
}

class IssueTablePanel {
  -issueService : IssueService
  -table : JTable
  +refresh() : void
}

class AdminDashboard {
  -admin : Admin
  -issueService : IssueService
  -applyFilter() : void
  -attemptStatusUpdate() : void
  -refreshTable() : void
  -logout() : void
}

' ===================================================================
'  relationships
' ===================================================================

' --- generalisation ---------------------------------------------------
User <|-- Citizen
User <|-- Admin

' --- association : a citizen files many issues ------------------------
Citizen "1" --> "0..*" Issue : reports

' --- service -> dao ---------------------------------------------------
AuthService  --> UserDAO  : userDAO
IssueService --> IssueDAO : issueDAO

' --- service -> service / model / util --------------------------------
AuthService  ..> ValidationService : <<use>>
IssueService ..> ValidationService : <<use>>
AuthService  ..> Citizen : <<create>>
AuthService  ..> Admin   : <<create>>
AuthService  ..> PasswordUtils : <<use>>
IssueService ..> Issue   : <<create>>

' --- dao -> record / database -----------------------------------------
UserDAO  ..> UserRecord : <<create>>
UserDAO  --> DBConnection : <<use>>
IssueDAO --> DBConnection : <<use>>

' --- util -------------------------------------------------------------
PasswordUtils ..> AppConstants : <<use>>

' --- ui -> service ----------------------------------------------------
LoginFrame         --> AuthService  : authService
RegistrationDialog --> AuthService  : authService
CitizenDashboard   --> IssueService : issueService
AdminDashboard     --> IssueService : issueService
IssueReportPanel   --> IssueService : issueService
IssueTablePanel    --> IssueService : issueService

' --- ui composition ---------------------------------------------------
LoginFrame       *-- RegistrationDialog : opens
CitizenDashboard *-- IssueReportPanel   : contains
CitizenDashboard *-- IssueTablePanel    : contains
AdminDashboard   *-- IssueTablePanel    : contains

@enduml
```

</details>

---

## 3. Object Diagram

![Object Diagram](uml/03_object.png)

A snapshot of the system at one instant — the way a class diagram shows
*structure*, an object diagram shows *instances*.

- The two `users` rows and the three `issues` rows are exactly the rows that
  `sql/insert_sample_data.sql` puts into `civiceye_db`.
- `citizenSession : Citizen` and `adminSession : Admin` are the objects held in
  memory by the two open windows.
- `issues.reporter_id` is a foreign key to `users.user_id` with
  `ON DELETE CASCADE`, which is why every `Issue` instance is linked to its
  `Citizen`.

<details><summary>PlantUML source — <code>uml/03_object.puml</code></summary>

```plantuml
@startuml civiceye_object
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 3 of 9 : OBJECT DIAGRAM
'  Notation: StarUML 5 — underlined "name : Class" rectangles.
'  A runtime snapshot: one logged-in Citizen, one Administrator and
'  the issue instances that exist at that moment.  The rows are the
'  ones created by sql/insert_sample_data.sql.
' ===================================================================
title CivicEye — Object Diagram (runtime snapshot)

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam BorderColor #000000
skinparam BorderThickness 1
skinparam classAttributeIconSize 0
skinparam nodesep 45
skinparam ranksep 55

skinparam object {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  AttributeFontColor #000000
  AttributeFontSize 10
}

skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 10
}

' --- session objects (in memory) --------------------------------------
object "<u>citizenSession : Citizen</u>" as o_citizen {
  userId = 2
  name = "Ravi Kumar"
  email = "citizen@civiceye.com"
  role = "Citizen"
}

object "<u>adminSession : Admin</u>" as o_admin {
  userId = 1
  name = "System Administrator"
  email = "admin@civiceye.com"
  role = "Admin"
}

object "<u>issueTableModel : DefaultTableModel</u>" as o_table {
  rowCount = 3
  selectedIssueId = 2
}

object "<u>issueService : IssueService</u>" as o_isvc {
  pendingCount = 1
  inProgressCount = 1
  resolvedCount = 1
}

' --- persistent objects (rows) ----------------------------------------
object "<u>admin : users</u>" as t_admin {
  user_id = 1
  name = "System Administrator"
  email = "admin@civiceye.com"
  password_hash = "01b79d24...a278864"
  salt = "a1b2c3d4e5f60001"
  role = "Admin"
}

object "<u>ravi : users</u>" as t_citizen {
  user_id = 2
  name = "Ravi Kumar"
  email = "citizen@civiceye.com"
  password_hash = "1960dc3f...53af057c"
  salt = "f0e1d2c3b4a50002"
  role = "Citizen"
}

object "<u>issue1 : issues</u>" as t_i1 {
  issue_id = 1
  reporter_id = 2
  category = "Roads"
  location = "MG Road, Bengaluru"
  description = "Large pothole near the traffic signal"
  status = "Pending"
  created_at = "2026-10-10 09:15:00"
}

object "<u>issue2 : issues</u>" as t_i2 {
  issue_id = 2
  reporter_id = 2
  category = "Sanitation"
  location = "Koramangala 4th Block"
  description = "Garbage not collected for five days"
  status = "In Progress"
  created_at = "2026-10-12 14:30:00"
}

object "<u>issue3 : issues</u>" as t_i3 {
  issue_id = 3
  reporter_id = 2
  category = "Street Lighting"
  location = "Indiranagar 100 Feet Road"
  description = "Three streetlights non-functional after 8 PM"
  status = "Resolved"
  created_at = "2026-10-14 18:00:00"
}

' --- links -------------------------------------------------------------
o_citizen --> t_citizen : session for
o_admin   --> t_admin   : session for

o_citizen --> t_i1 : reported
o_citizen --> t_i2 : reported
o_citizen --> t_i3 : reported

o_table --> t_i1 : displays
o_table --> t_i2 : displays
o_table --> t_i3 : displays

o_admin --> o_table : selects row 2
o_admin --> o_isvc : 1: updateStatus(2, "Resolved")
o_isvc  --> t_i2  : sets status = "Resolved"

note bottom of t_i2
  reporter_id is a FOREIGN KEY to
  users(user_id) with ON DELETE CASCADE,
  so deleting the citizen deletes the
  three issue rows as well.
end note

@enduml
```

</details>

---

## 4. Sequence Diagram

![Sequence Diagram](uml/04_sequence.png)

One straight scenario — the citizen logs in, the correct dashboard is opened
and an issue is reported — with no actor symbols and no combined fragments.
Vertical position is time, horizontal position is the object.

- Every participant is a **rectangle with an underlined object name**
  (`loginFrame : LoginFrame`), the StarUML way of drawing a lifeline: no stick
  figures anywhere in the diagram.
- **Login:** `loginFrame → authService → userDAO → civiceye_db`, and the stored
  SHA-256 hash is compared by `passwordUtils.verifyPassword()`.
- **Dashboard:** `loginFrame` opens the role-specific window, which asks
  `issueService` for the issues of that reporter.
- **Reporting:** `issueReportPanel` calls `issueService.submitIssue()`, which
  validates the form through `validationService` and lets `issueDAO` insert the
  row with status `Pending`.
- Solid arrows are calls, dashed open arrows are returns; the numbered sequence
  reads 1 → 27 from top to bottom.

<details><summary>PlantUML source — <code>uml/04_sequence.puml</code></summary>

```plantuml
@startuml civiceye_sequence
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 4 of 9 : SEQUENCE DIAGRAM
'  Notation: StarUML 5 — no actor symbols; every participant is an
'  underlined object rectangle.  Solid arrows are calls, dashed open
'  arrows are returns.  One straight scenario, no combined fragments.
' ===================================================================
title CivicEye — Sequence Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam ArrowFontSize 10
skinparam BorderColor #000000
skinparam BorderThickness 1

skinparam sequence {
  ActorBorderColor #000000
  ActorBackgroundColor #FFFFFF
  LifeLineBorderColor #000000
  LifeLineBackgroundColor #FFFFFF
  ParticipantBorderColor #000000
  ParticipantBackgroundColor #FFFFFF
  ParticipantFontColor #000000
  ParticipantFontSize 11
  ParticipantPadding 18
  DividerBorderColor #000000
  DividerBackgroundColor #FFFFFF
  DividerFontColor #000000
  GroupBorderColor #000000
  GroupBackgroundColor #FFFFFF
  NoteBorderColor #000000
  NoteBackgroundColor #FFFFFF
  NoteFontColor #000000
  ReferenceBorderColor #000000
  ReferenceBackgroundColor #FFFFFF
}

participant "<u>loginFrame : LoginFrame</u>"                  as LF
participant "<u>authService : AuthService</u>"                as AS
participant "<u>passwordUtils : PasswordUtils</u>"            as PU
participant "<u>userDAO : UserDAO</u>"                        as UD
participant "<u>civiceye_db : MySQL 8</u>"                    as DB
participant "<u>citizenDashboard : CitizenDashboard</u>"      as CD
participant "<u>issueReportPanel : IssueReportPanel</u>"      as IRP
participant "<u>validationService : ValidationService</u>"    as VS
participant "<u>issueService : IssueService</u>"              as IS
participant "<u>issueDAO : IssueDAO</u>"                      as ID

' ===================================================================
'  1 - 9 : the citizen authenticates
' ===================================================================
LF  ->  AS  : 1: login(email, password)
activate AS

AS  ->  UD  : 2: findByEmail(email)
activate UD
UD  ->  DB  : 3: SELECT user_id, password_hash,\nsalt, role FROM users\nWHERE email = ?
DB  -->  UD  : 4: 0..1 row
UD  -->  AS  : 5: UserRecord
deactivate UD

AS  ->  PU  : 6: verifyPassword(plaintext, storedHash, salt)
activate PU
PU  -->  AS  : 7: true
deactivate PU

AS  ->  AS  : 8: build Citizen or Admin from role
AS  -->  LF  : 9: User
deactivate AS

' ===================================================================
'  10 - 16 : the role-specific dashboard is opened
' ===================================================================
LF  ->  CD  : 10: setVisible(true)
activate CD
CD  ->  IS  : 11: getIssuesByReporter(userId)
activate IS
IS  ->  ID  : 12: getIssuesByReporter(reporterId)
activate ID
ID  ->  DB  : 13: SELECT * FROM issues\nWHERE reporter_id = ?\nORDER BY created_at DESC
DB  -->  ID  : 14: ResultSet
ID  -->  IS  : 15: List<Issue>
deactivate ID
IS  -->  CD  : 16: List<Issue>
deactivate IS

' ===================================================================
'  17 - 25 : the citizen reports a new issue
' ===================================================================
CD  ->  IRP  : 17: open the report form
activate IRP
IRP  ->  IS  : 18: submitIssue(reporterId, category, location, description)
activate IS

IS  ->  VS  : 19: validateIssueForm(category, location, description)
activate VS
VS  -->  IS  : 20: null (report is valid)
deactivate VS

IS  ->  ID  : 21: createIssue(issue)
activate ID
ID  ->  DB  : 22: INSERT INTO issues (...)\nVALUES (?, ?, ?, ?, 'Pending')
DB  -->  ID  : 23: rows affected = 1
ID  -->  IS  : 24: true
deactivate ID

IS  -->  IRP  : 25: the persisted Issue (status = "Pending")
deactivate IS

' ===================================================================
'  26 - 27 : the table is refreshed with the new issue
' ===================================================================
IRP ->  CD  : 26: refreshTable()
deactivate IRP
CD  ->  CD  : 27: rebuild the table model from List<Issue>
deactivate CD

@enduml
```

</details>

---

## 5. Collaboration Diagram

![Collaboration Diagram](uml/05_collaboration.png)

The same interaction as the sequence diagram, but drawn as the *network* of
objects instead of the time line.

- Objects are arranged in three layers — boundary objects on top, control
  objects in the middle, entity objects and the database below — so the links
  run downwards instead of across the page.
- Every link carries the call **and** its return value, numbered in the order
  in which they happen (1 → 26), exactly as a StarUML collaboration diagram
  does it.
- Messages 1‑9 are the authentication collaboration, 10‑18 the reporting
  collaboration and 19‑26 the status-update collaboration.
- `ValidationService` appears in two collaborations because it is shared by
  both flows — it is the only place where the rules live.

<details><summary>PlantUML source — <code>uml/05_collaboration.puml</code></summary>

```plantuml
@startuml civiceye_collaboration
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 5 of 9 : COLLABORATION (COMMUNICATION) DIAGRAM
'  Notation: StarUML 5 — underlined object rectangles joined by links;
'  the order of the interaction is given by the message numbers.
'  Each link carries the call and the return value.
' ===================================================================
title CivicEye — Collaboration Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam ArrowFontSize 9
skinparam BorderColor #000000
skinparam BorderThickness 1
skinparam classAttributeIconSize 0
skinparam nodesep 30
skinparam ranksep 45

skinparam object {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
}

skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 9
}

object "<u>loginFrame : LoginFrame</u>"                  as o_login
object "<u>issueReportPanel : IssueReportPanel</u>"      as o_report
object "<u>adminDashboard : AdminDashboard</u>"          as o_admin

object "<u>authService : AuthService</u>"                as o_auth
object "<u>validationService : ValidationService</u>"    as o_val
object "<u>issueService : IssueService</u>"              as o_isvc

object "<u>passwordUtils : PasswordUtils</u>"            as o_pwd
object "<u>userDAO : UserDAO</u>"                        as o_udao
object "<u>issueDAO : IssueDAO</u>"                      as o_idao
object "<u>civiceye_db : MySQL 8</u>"                    as o_mysql

' --- authentication : messages 1 - 9 ----------------------------------
o_login --> o_auth  : 1: login(email, password)\n9: User
o_auth  --> o_udao  : 2: findByEmail(email)\n5: UserRecord
o_udao  --> o_mysql : 3: SELECT ... FROM users\n4: 0..1 row
o_auth  --> o_pwd   : 6: verifyPassword(...)\n7: true
o_auth  --> o_auth  : 8: build Citizen | Admin

' --- reporting : messages 10 - 18 -------------------------------------
o_login  --> o_report : 10: openReportForm()
o_report --> o_isvc   : 11: submitIssue(...)\n18: Issue (Pending)
o_isvc   --> o_val    : 12: validateIssueForm(...)\n13: null
o_isvc   --> o_idao   : 14: createIssue(issue)\n17: true
o_idao   --> o_mysql  : 15: INSERT INTO issues\n16: 1 row

' --- status update : messages 19 - 26 ---------------------------------
o_admin --> o_isvc   : 19: updateStatus(...)\n26: Status updated
o_isvc  --> o_val    : 20: validateStatusTransition(...)\n21: true
o_isvc  --> o_idao   : 22: updateIssueStatus(...)\n25: true
o_idao  --> o_mysql  : 23: UPDATE issues\n24: 1 row

note bottom of o_val
  ValidationService is shared by both
  collaborations: it is the only place
  where the form rules and the status
  transition rules live.
end note

@enduml
```

</details>

---

## 6. State Machine Diagram

![State Machine Diagram](uml/06_state_machine.png)

The dynamic behaviour of the two objects that have a life cycle of their own.

- **Issue:** a new report is always created as `Pending`. An administrator may
  move it to `In Progress` or straight to `Resolved`; `Resolved` is terminal.
- **Citizen Session:** `Anonymous → Authenticating → Authenticated`, with the
  failure path back to `Anonymous` and `logout()` closing the session.

<details><summary>PlantUML source — <code>uml/06_state_machine.puml</code></summary>

```plantuml
@startuml civiceye_state
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 6 of 9 : STATE MACHINE DIAGRAM
'  Notation: StarUML 5 — rounded state boxes, filled initial pseudo
'  state, bull's-eye final state, black transitions.
' ===================================================================
title CivicEye — State Machine Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam BorderColor #000000
skinparam BorderThickness 1

skinparam state {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  FontStyle bold
  AttributeFontColor #000000
  AttributeFontSize 10
  AttributeFontStyle normal
}
skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 10
}

' ===================================================================
'  MACHINE 1 : lifecycle of a civic issue
' ===================================================================
state "Issue" as IssueMachine {

  state "Pending" as Pending {
    Pending : status = "Pending"
    Pending : createdAt is set
  }

  state "In Progress" as InProgress {
    InProgress : status = "In Progress"
    InProgress : administrator started the work
  }

  state "Resolved" as Resolved {
    Resolved : status = "Resolved"
    Resolved : terminal state
  }

  [*] --> Pending : submitIssue()
  Pending    --> InProgress : Administrator sets\n"In Progress"   [transition allowed]
  Pending    --> Resolved   : Administrator resolves directly   [transition allowed]
  InProgress --> Resolved   : Administrator marks "Resolved"     [transition allowed]
  Resolved   --> [*]        : issue closed
}

' ===================================================================
'  MACHINE 2 : lifecycle of a citizen session
' ===================================================================
state "Citizen Session" as Session {

  state "Anonymous" as Anonymous
  state "Authenticating" as Authenticating
  state "Authenticated" as Authenticated

  [*] --> Anonymous
  Anonymous       --> Authenticating : login(email, password)
  Authenticating  --> Anonymous      : invalid credentials
  Authenticating  --> Authenticated  : credentials valid
  Authenticated   --> Anonymous      : logout()
}

note right of Pending
  A new issue is always created
  in Pending. The two remaining
  transitions are executed only by
  an Administrator and are checked
  by validateStatusTransition().
end note

note top of Authenticating
  The password is compared as
  SHA-256(password + salt) with
  users.password_hash.
end note

@enduml
```

</details>

---

## 7. Activity Diagram

![Activity Diagram](uml/07_activity.png)

The control flow of the application across five swim-lanes.

- **Citizen** — enters the credentials, fills in the report.
- **Swing UI** — validates the input, shows the messages, refreshes the issues.
- **Service** — the business rules: login, submission, validation and the
  status transition.
- **Database** — the `SELECT`, the `INSERT` and the `UPDATE`.
- **Administrator** — reviews the report and moves the issue through
  `Pending → In Progress → Resolved`.
- **Fork / join** — once the login succeeds the token is **forked**: the Citizen
  Portal reports an issue while, concurrently, the Admin Portal updates an
  existing one. The thick black fork bar splits the flow, the matching join bar
  merges the two branches again before `start` reaches its final state.
- The diamonds are the two decisions the application really makes: is the input
  well formed, and are the credentials valid.

<details><summary>PlantUML source — <code>uml/07_activity.puml</code></summary>

```plantuml
@startuml civiceye_activity
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 7 of 9 : ACTIVITY DIAGRAM
'  Notation: StarUML 5 — swim-lane partitions, initial / final pseudo
'  states, decision diamonds, FORK and JOIN bars.
'  After a successful login the Citizen Portal and the Admin Portal
'  work concurrently on the same database: the token is forked into
'  the two lanes and joined again before the application is closed.
' ===================================================================
title CivicEye — Activity Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam BorderColor #000000
skinparam BorderThickness 1

skinparam activity {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  DiamondBackgroundColor #FFFFFF
  DiamondBorderColor #000000
  DiamondFontColor #000000
}
skinparam swimlane {
  BorderColor #000000
  TitleBackgroundColor #FFFFFF
  TitleFontColor #000000
  TitleFontSize 12
  TitleFontStyle bold
}
skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 10
}

|Citizen|
start
:Open CivicEye;
:Enter email and password;

|Swing UI|
:validateLoginForm(email, password);

if (Form well formed?) then (no)
  :Show "Enter a valid email and password";
  stop
else (yes)

  |Service|
  :login(email, password);
  :verifyPassword(plaintext, hash, salt);

  if (Credentials valid?) then (no)
    |Swing UI|
    :Show "Invalid email or password";
    stop
  else (yes)

    |Swing UI|
    :Open CitizenDashboard\nor AdminDashboard;

    ' ===============================================================
    '  FORK — the two portals now work concurrently
    ' ===============================================================
    fork

      |Citizen|
      :Choose category, location\nand description;
      :Click Submit;

      |Swing UI|
      :attemptSubmit();

      |Service|
      :validateIssueForm(category, location, description);
      :createIssue(issue);

      |Database|
      :INSERT INTO issues\n(status = "Pending");

    fork again

      |Administrator|
      :Select the reported issue;
      :Choose the new status;
      :Click Update Status;

      |Service|
      :validateStatusTransition(current, new);
      :updateIssueStatus(issueId, newStatus);

      |Database|
      :UPDATE issues\nSET status = ?;

    end fork
    ' ===============================================================
    '  JOIN — both portals are idle again
    ' ===============================================================

    |Swing UI|
    :Refresh both tables;
    :Show the result of the update;

    stop

  endif

endif

@enduml
```

</details>

---

## 8. Component Diagram

![Component Diagram](uml/08_component.png)

How the code is packaged and what it depends on.

- `civiceye.jar` is the single package; the dashed arrows are unlabelled UML
  dependencies, always pointing from the caller to the callee.
- The UI depends on the services, the services depend on the DAOs, and the
  DAOs depend on `DBConnection` and on the MySQL connector JAR.
- `config.properties` is read by `DBConnection`; it holds `db.url`,
  `db.user` and `db.password`.
- The MySQL server is an external `«DBMS»` node — it is not part of the build.

<details><summary>PlantUML source — <code>uml/08_component.puml</code></summary>

```plantuml
@startuml civiceye_component
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 8 of 9 : COMPONENT DIAGRAM
'  Notation: StarUML 5 — component rectangles with the component
'  icon, package folders, dashed dependency arrows.
' ===================================================================
title CivicEye — Component Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam ArrowFontSize 10
skinparam BorderColor #000000
skinparam BorderThickness 1

skinparam component {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  FontStyle bold
}
skinparam package {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontStyle bold
  FontSize 12
}
skinparam database {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
}
skinparam artifact {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
}
skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 10
}

package "civiceye.jar" {

  component "UI\n«subsystem»" as UI {
    component "LoginFrame\nRegistrationDialog"        as C_LOGIN
    component "CitizenDashboard\nIssueReportPanel"    as C_CIT
    component "AdminDashboard\nIssueTablePanel"       as C_ADM
  }

  component "Service\n«subsystem»" as SVC {
    component "AuthService"        as C_AUTH
    component "IssueService"       as C_ISSUE
    component "ValidationService"  as C_VAL
  }

  component "DAO\n«subsystem»" as DAO {
    component "UserDAO"    as C_UDAO
    component "IssueDAO"   as C_IDAO
    component "UserRecord" as C_UREC
  }

  component "DBConnection"      as C_DB
  component "PasswordUtils"     as C_PWD
  component "AppConstants"      as C_CONST
}

artifact "config.properties"             as A_CFG
artifact "mysql-connector-j.jar"         as A_JDBC
database "MySQL 8 Server\n«DBMS»"        as N_MYSQL

' --- UI -> Service ----------------------------------------------------
C_LOGIN ..> C_AUTH
C_CIT   ..> C_ISSUE
C_ADM   ..> C_ISSUE

' --- Service -> Service / DAO -----------------------------------------
C_AUTH  ..> C_VAL
C_ISSUE ..> C_VAL
C_AUTH  ..> C_UDAO
C_ISSUE ..> C_IDAO
C_AUTH  ..> C_PWD

' --- DAO -> DBConnection ----------------------------------------------
C_UDAO ..> C_DB
C_IDAO ..> C_DB
C_UDAO ..> C_UREC

' --- internal / external dependencies ---------------------------------
C_DB    ..> A_CFG
C_PWD   ..> C_CONST
C_DB    ..> A_JDBC
A_JDBC  ..> N_MYSQL

note right of A_CFG
  db.url / db.user / db.password
  are resolved from the classpath,
  then the working directory,
  then the built-in defaults.
end note

note bottom of N_MYSQL
  Tables: users, issues.
  issues.reporter_id is a FOREIGN KEY
  to users.user_id with ON DELETE CASCADE.
end note

@enduml
```

</details>

---

## 9. Deployment Diagram

![Deployment Diagram](uml/09_deployment.png)

The runtime topology of the installation.

- The application is a desktop client: two `«device»` nodes (Citizen PC and
  Administrator PC) hold the deployed `civiceye.jar` and
  `mysql-connector-j.jar`; the JRE 17+ and `config.properties` are noted on the
  node itself.
- Both workstations talk to the `«server»` node over JDBC/TCP 3306.
- Inside the server, the `«execution environment»` *MySQL 8.0* hosts the
  *MySQL Server 8.0* artifact and the `civiceye_db` database.

<details><summary>PlantUML source — <code>uml/09_deployment.puml</code></summary>

```plantuml
@startuml civiceye_deployment
' ===================================================================
'  CivicEye — Civic Issue Reporting & Management System
'  Diagram 9 of 9 : DEPLOYMENT DIAGRAM
'  Notation: StarUML 5 — 3-D node boxes, artifacts nested inside the
'  node they are deployed on, solid communication associations with
'  the protocol as stereotype.
' ===================================================================
title CivicEye — Deployment Diagram

skinparam backgroundColor #FFFFFF
skinparam shadowing false
skinparam defaultFontName Arial
skinparam defaultFontSize 11
skinparam defaultFontColor #000000
skinparam ArrowColor #000000
skinparam ArrowFontColor #000000
skinparam ArrowFontName Arial
skinparam BorderColor #000000
skinparam BorderThickness 1
skinparam nodesep 55
skinparam ranksep 60

skinparam node {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 11
  FontStyle bold
}
skinparam artifact {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
  FontSize 10
}
skinparam database {
  BackgroundColor #FFFFFF
  BorderColor #000000
  FontColor #000000
}
skinparam note {
  BackgroundColor #FFFFFF
  BorderColor #000000
  NoteFontColor #000000
  NoteFontSize 10
}

' ===================================================================
'  CLIENT NODES
' ===================================================================
node "«device»\nCitizen PC\n«OS» Windows / Linux" as N_CIT {
  artifact "civiceye.jar"                as A_APP_CIT
  artifact "mysql-connector-j.jar"       as A_JDBC_CIT
}

node "«device»\nAdministrator PC\n«OS» Windows / Linux" as N_ADM {
  artifact "civiceye.jar"                as A_APP_ADM
  artifact "mysql-connector-j.jar"       as A_JDBC_ADM
}

' ===================================================================
'  SERVER NODE
' ===================================================================
node "«server»\nDatabase Server" as N_DB {
  node "«execution environment»\nMySQL 8.0" as N_MYSQL {
    artifact "MySQL Server 8.0"          as A_MYSQL
    database "civiceye_db"               as DB_CIVIC
  }
}

' ===================================================================
'  COMMUNICATION
' ===================================================================
N_CIT --> N_MYSQL : «JDBC»
N_ADM --> N_MYSQL : «JDBC»

note top of N_CIT
  JRE 17+, civiceye.jar,
  mysql-connector-j.jar and
  config.properties are
  installed on both PCs.
end note

note top of N_ADM
  Admin Portal — review an
  issue and move it through
  Pending -> In Progress -> Resolved.
end note

note right of N_MYSQL
  Only TCP port 3306 is
  exposed. The credentials
  come from config.properties.
end note

note bottom of DB_CIVIC
  users(user_id PK, name, email UNIQUE,
  password_hash, salt, role, created_at)
  issues(issue_id PK, reporter_id FK -> users,
  category, location, description, status,
  created_at)
end note

@enduml
```

</details>

---

## Figure index

| # | Diagram | PNG | SVG | Source |
|---|---|---|---|---|
| 1 | Use Case Diagram | [png](uml/01_use_case.png) | [svg](uml/01_use_case.svg) | [puml](uml/01_use_case.puml) |
| 2 | Class Diagram | [png](uml/02_class.png) | [svg](uml/02_class.svg) | [puml](uml/02_class.puml) |
| 3 | Object Diagram | [png](uml/03_object.png) | [svg](uml/03_object.svg) | [puml](uml/03_object.puml) |
| 4 | Sequence Diagram | [png](uml/04_sequence.png) | [svg](uml/04_sequence.svg) | [puml](uml/04_sequence.puml) |
| 5 | Collaboration Diagram | [png](uml/05_collaboration.png) | [svg](uml/05_collaboration.svg) | [puml](uml/05_collaboration.puml) |
| 6 | State Machine Diagram | [png](uml/06_state_machine.png) | [svg](uml/06_state_machine.svg) | [puml](uml/06_state_machine.puml) |
| 7 | Activity Diagram | [png](uml/07_activity.png) | [svg](uml/07_activity.svg) | [puml](uml/07_activity.puml) |
| 8 | Component Diagram | [png](uml/08_component.png) | [svg](uml/08_component.svg) | [puml](uml/08_component.puml) |
| 9 | Deployment Diagram | [png](uml/09_deployment.png) | [svg](uml/09_deployment.svg) | [puml](uml/09_deployment.puml) |
