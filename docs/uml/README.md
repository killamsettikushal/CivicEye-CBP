# CivicEye — UML Diagrams

The nine UML diagrams required by the *UNIT III (Part-2)* presentation, modelled
against the actual CivicEye implementation in `src/civiceye/**`.

All nine are drawn in **StarUML notation**: white fill, black 1 px outline,
black Arial text, stick-figure actors, underlined object rectangles, thick
fork/join bars and 3-D deployment nodes. No colour fills, no icons, no legend
tables inside the diagrams.

Every diagram has three artefacts:

| Artefact | Purpose |
|---|---|
| `NN_name.puml` | PlantUML source — edit this and re-render |
| `NN_name.png` | 2× raster image, ready to paste into the CBP report |
| `NN_name.svg` | Vector version, scales without blur in Word / LaTeX |

Re-render everything after an edit:

```bash
cd docs/uml
./render.sh          # needs a JDK and plantuml.jar (see the script header)
```

---

## The nine diagrams

| # | Diagram | Source | What it shows |
|---|---|---|---|
| 1 | Use Case | `01_use_case.puml` | `Citizen` and `Administrator` stick figures against the CivicEye system boundary, four `«include»` relationships and straight association lines |
| 2 | Class | `02_class.puml` | The full static model as plain class rectangles — no package frames — with attributes, operations, multiplicities and the `User` → `Citizen` / `Admin` generalisation |
| 3 | Object | `03_object.puml` | A runtime snapshot: the logged-in `Citizen`, the `Administrator`, three `Issue` instances and the in-memory session objects, with their links |
| 4 | Sequence | `04_sequence.puml` | One time-ordered scenario (login → dashboard → report an issue) with underlined object rectangles, activation bars, numbered calls and returns — **no actor symbols, no `alt` fragments** |
| 5 | Collaboration | `05_collaboration.puml` | The same interaction drawn as a three-layer network of objects with 26 numbered messages (1‑9 authentication, 10‑18 reporting, 19‑26 status update) |
| 6 | State Machine | `06_state_machine.puml` | The `Issue` lifecycle (`Pending → In Progress → Resolved`, `Resolved` terminal) and the `Citizen Session` machine (anonymous → authenticating → authenticated) |
| 7 | Activity | `07_activity.puml` | The end-to-end workflow across five swim-lanes — Citizen, Swing UI, Service, Database, Administrator — with decision diamonds and a **fork / join** pair for the two concurrent portals |
| 8 | Component | `08_component.puml` | The physical packaging: the `civiceye.jar` application broken into its layers, plus `config.properties`, the MySQL connector JAR and the external MySQL server |
| 9 | Deployment | `09_deployment.puml` | Runtime topology: `«device»` workstations holding the deployed JARs, talking to the `«server»` node that runs the MySQL execution environment over JDBC/TCP 3306 |

---

## Correspondence with the presentation

The order matches the nine worked examples in *UNIT III (Part-2)* (the ATM
examples): use case, class, object, sequence, collaboration, state chart,
activity, component, deployment. The subject matter is CivicEye instead of the
ATM, but each diagram keeps the structure and the notation of its model.

---

## Earlier Mermaid variants

`mermaid/` holds an earlier, text-only Mermaid rendering of the same nine
diagrams. It is kept for reference (it pastes straight into GitHub, Notion or
VS Code); the PlantUML set above is the canonical one because it produces
proper UML notation — class compartments, `<<include>>` / `<<extend>>`
stereotypes, lifelines, activation bars and swim-lanes.
