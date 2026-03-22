# Semantic HTML Renderer for Pneuma

## Goal

Render a pneuma architecture document — formalisms, morphisms,
composed paths, and gap report — as a browsable static HTML file.
No HTTP server required; open the file directly in a browser.

## Design Basis

Applies the semantic-UI pattern: containers declare semantic context,
leaf fragments react by selecting presentation tokens. Visual treatment
is driven entirely by CSS via `data-` attributes. The Clojure code
emits semantic markup only.

## Semantic Context

A context map propagates down the fragment tree during rendering:

```clojure
{:intent   keyword   ; :detail | :summary | :list-item | :hero
 :priority keyword   ; :high | :medium | :low | nil
 :frame    keyword   ; :object | :morphism | :path | :document
 :depth    nat-int   ; section nesting level (0 = root)
 :base-url string}   ; prefix for cross-ref hrefs (default "")
```

### Signal derivation

| Signal     | Source                                                         |
|------------|----------------------------------------------------------------|
| `intent`   | Set by the page structure. Root sections get `:detail`.        |
|            | Gap report summary gets `:hero`. Morphism endpoints            |
|            | appearing inline get `:summary` or `:list-item`.               |
| `priority` | Inferred from `:status-annotation` children of a fragment.     |
|            | `:diverges` → `:high`, `:absent` → `:medium`,                 |
|            | `:conforms` → `:low`. Inherits from parent when absent.        |
| `frame`    | Derived from section id namespace: `:morphism/*` → `:morphism`,|
|            | `:path/*` → `:path`, `:gap/*` → `:document`, else `:object`.  |

### Context update at section boundaries

```clojure
(defn section-ctx
  "Derives child context from a section fragment and current context."
  [section ctx]
  (-> ctx
      (update :depth inc)
      (assoc :priority (or (infer-priority section) (:priority ctx)))
      (assoc :frame (infer-frame (:id section) (:frame ctx)))))
```

`infer-priority` scans immediate children for `:status-annotation`
fragments and returns the highest-severity status found, or nil.

## Rendering Dispatch

A multimethod dispatches on `[fragment-kind intent]`:

```clojure
(defmulti render-fragment
  "Renders a fragment to hiccup given a semantic context."
  (fn [fragment ctx] [(:kind fragment) (:intent ctx)]))
```

This allows the same fragment kind to produce different markup
depending on where it appears. A `:default` method handles the
common case; specific `[kind intent]` pairs override when needed.

## Fragment → HTML Mapping

### Section

Renders as a `<section>` element. Sections below depth 1 use
`<details>/<summary>` for collapsibility.

```html
<section id="statechart" data-intent="detail" data-priority="high" data-frame="object">
  <h2>Statechart</h2>
  <!-- children -->
</section>
```

At depth ≥ 2:

```html
<details id="statechart/transitions" data-priority="low" open>
  <summary>Transitions</summary>
  <!-- children -->
</details>
```

The gap report summary section uses `intent=hero`:

```html
<header id="gap/summary" data-intent="hero" data-priority="high">
  <h2>Gap Report</h2>
  <!-- summary content -->
</header>
```

### Table

Renders as a `<table>` with column headers. The frame attribute
indicates the table's domain layer.

```html
<table id="morphism/table" data-frame="morphism">
  <thead><tr><th>id</th><th>kind</th><th>from</th><th>to</th></tr></thead>
  <tbody>
    <tr><td>...</td><td>...</td><td>...</td><td>...</td></tr>
  </tbody>
</table>
```

### Prose

Renders as a `<p>` element.

```html
<p id="gap/summary">Total gaps: 12. Conforming: 10. Failures: 2.</p>
```

### Diagram Spec

Renders as a `<pre class="mermaid">` block. The mermaid.js library
(loaded from CDN) processes these client-side into SVG.

```html
<pre class="mermaid">
stateDiagram-v2
  uninitialized
  running
  uninitialized --> running : init
</pre>
```

The Clojure code reuses the existing `render-mermaid-*` functions
to produce the diagram text.

### Cross-Ref

Renders as an `<a>` linking to the target fragment's anchor.

```html
<a href="#statechart" class="cross-ref">Statechart</a>
```

### Status Annotation

Renders as a `<span>` badge with `data-status` for CSS targeting.

```html
<span class="badge" data-status="diverges">DIVERGES</span>
<span class="badge" data-status="conforms">CONFORMS</span>
```

When the annotation has detail, it renders as a tooltip:

```html
<span class="badge" data-status="diverges"
      title="reason: dangling-refs, missing: [:halt-key!]">DIVERGES</span>
```

## Page Shell

A single HTML page wraps the rendered fragment tree:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{document title}</title>
  <script src="https://cdn.jsdelivr.net/npm/mermaid/dist/mermaid.min.js"></script>
  <script>mermaid.initialize({startOnLoad: true});</script>
  <style>/* inline CSS — see CSS section */</style>
</head>
<body>
  <nav id="toc"><!-- generated table of contents --></nav>
  <main>{rendered fragment tree}</main>
</body>
</html>
```

### Table of Contents

Generated from the section tree. Top-level sections become nav links:

```html
<nav id="toc">
  <ul>
    <li><a href="#statechart">Statechart</a></li>
    <li><a href="#effect-signature">Effect Signature</a></li>
    <li><a href="#morphism/root">Morphism Connections</a></li>
    <li><a href="#path/root">Composed Paths</a></li>
    <li><a href="#gap/root">Gap Report</a></li>
  </ul>
</nav>
```

## CSS Design

All visual treatment is driven by `data-` attributes. The Clojure
code never names colours or sizes.

```css
:root {
  --color-conforms:    #16a34a;
  --color-conforms-bg: #f0fdf4;
  --color-absent:      #d97706;
  --color-absent-bg:   #fffbeb;
  --color-diverges:    #dc2626;
  --color-diverges-bg: #fef2f2;
}

/* Priority — left border accent on sections */
[data-priority="high"]   { border-left: 3px solid var(--color-diverges); }
[data-priority="medium"] { border-left: 3px solid var(--color-absent); }
[data-priority="low"]    { border-left: 3px solid var(--color-conforms); }

/* Status badges */
.badge { padding: 2px 6px; border-radius: 3px; font-size: 0.8em; font-weight: 600; }
.badge[data-status="conforms"] { background: var(--color-conforms-bg); color: var(--color-conforms); }
.badge[data-status="absent"]   { background: var(--color-absent-bg);   color: var(--color-absent); }
.badge[data-status="diverges"] { background: var(--color-diverges-bg); color: var(--color-diverges); }

/* Hero intent — gap report header */
[data-intent="hero"] { padding: 1em; margin-bottom: 1em; border: 1px solid #e5e7eb; border-radius: 6px; }

/* Tables */
table { border-collapse: collapse; width: 100%; margin: 1em 0; }
th, td { padding: 0.4em 0.8em; border: 1px solid #e5e7eb; text-align: left; }
th { background: #f9fafb; font-weight: 600; }

/* Navigation */
#toc { position: sticky; top: 0; background: white; padding: 0.5em 1em; border-bottom: 1px solid #e5e7eb; }
#toc ul { list-style: none; display: flex; gap: 1em; padding: 0; margin: 0; }

/* Cross-refs */
a.cross-ref { text-decoration: none; border-bottom: 1px dashed currentColor; }

/* Details/summary sections */
details { margin: 0.5em 0; padding-left: 1em; }
summary { cursor: pointer; font-weight: 600; }

/* Layout */
main { max-width: 60em; margin: 0 auto; padding: 1em; font-family: system-ui, sans-serif; }
```

## File Layout

```
src/pneuma/doc/
  fragment.clj          — unchanged
  render.clj            — render-html delegates to html.page/render-page
  html/
    context.clj         — default-ctx, section-ctx, infer-priority, infer-frame
    fragment.clj        — render-fragment multimethod + all methods
    page.clj            — page shell, TOC generation, CSS, mermaid script
    mermaid.clj         — extracted from render.clj (currently private fns)
```

### Dependency graph

```
render.clj → html.page
html.page → html.fragment, html.mermaid
html.fragment → html.context, html.mermaid
html.context → (pure functions, no deps)
html.mermaid → (pure functions, no deps)
```

## Public API

Two entry points, matching the existing `render-markdown` signature:

```clojure
;; In pneuma.doc.render
(defn render-html
  "Renders a fragment tree to a self-contained HTML string."
  [fragment]
  (html.page/render-page fragment {}))

;; In pneuma.doc.html.page
(defn render-page
  "Renders a fragment tree to a complete HTML page string.
  Options: :title, :intent, :base-url."
  [fragment opts]
  ...)
```

The existing `render-doc` in `pneuma.doc.core` already dispatches
on `:format :html`, so no changes needed there — it calls
`render/render-html` which currently throws.

## Rendering Pipeline

```
render-doc (pneuma.doc.core)
  → assembles fragment tree from formalisms, morphisms, paths
  → annotate-with-gaps overlays :status-annotation nodes
  → render-html (pneuma.doc.render)
    → render-page (pneuma.doc.html.page)
      → build-toc from section tree
      → render-fragment recursively with ctx propagation
      → wrap in page shell with CSS + mermaid.js
    → returns self-contained HTML string
```

## Hiccup Dependency

Uses `hiccup.core/html` for serialization. Add to `deps.edn`:

```clojure
hiccup/hiccup {:mvn/version "2.0.0-RC4"}
```

## Testing Approach

Tests use hand-crafted fragment trees (no formalism records needed):

```clojure
(deftest render-section-with-gap-annotation
  ;; Verifies that gap status annotations propagate as data-priority
  ;; attributes and render as badge spans.
  (testing "render-fragment"
    (testing "sets data-priority from status-annotation children"
      (let [frag (doc/section :test/s "Test"
                   [(doc/prose :test/p "content")
                    (doc/status-annotation :test/s :diverges {:reason :x})])
            html (render-fragment frag (default-ctx))]
        (is (str/includes? html "data-priority=\"high\""))
        (is (str/includes? html "data-status=\"diverges\""))))))
```

## Scope Exclusions

- No JavaScript framework. Interactive behaviour limited to native
  `<details>/<summary>` collapsibility and mermaid.js diagram rendering.
- No server-side rendering or live reload.
- No multi-page generation in this iteration. Single self-contained
  HTML file. Multi-page can be added later by splitting sections
  into separate files with relative cross-ref hrefs.
- No source code rendering. Source file browsing is a future extension
  that would add a `:source-ref` fragment kind linking to syntax-highlighted
  source files.
