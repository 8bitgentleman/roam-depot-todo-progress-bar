# Roam Research Progress Bar Component

A highly customizable visual progress bar component for tracking TODOs in your Roam Research graph.

## Features
- Track TODO/DONE items within a block and all its children
- Three visualization styles: horizontal bar, radial circle, and hill diagram
- Hill diagram shows progress as a dot moving over a bell curve — left side is planning, right side is execution
- Multi-task hill mode: each top-level child block gets its own colored dot
- Customizable status text label
- Option to show completion percentage
- Option to exclude embedded/reference-only blocks from counting
- Inline compatibility — works within text without breaking lines
- Configurable through a built-in settings menu
- Real-time progress tracking

## Example
<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/example.gif" max-width="400"></img>

## Setup
First make sure that __User code__ is enabled in your settings. This allows custom components in your graph.

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/settings.png" width="300"></img>

## Usage
The easiest way to insert the component is through Roam's native template menu. Simply type `;;` and look for __TODO Progress Bar__

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/template.png" max-width="400"></img>

## Customization

### Using the Settings Menu
The component includes a built-in settings menu. Hover over the progress bar and click the gear icon that appears:

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/settings-menu-example.png" width="300"></img>

In the settings menu you can:
- Switch between horizontal bar, radial circle, and hill diagram styles
- Customize the status text label
- Toggle percentage display
- Exclude reference-only blocks from the count
- *(Hill only)* Show or hide the phase labels beneath the curve
- *(Hill only)* Enable multi-task mode

### Manual Customization
Parameters are passed positionally as quoted strings after the component UID.

```
{{roam/render: ((UID)) "style" "status-text" "show-percent" "exclude-blockrefs" "show-hill-labels" "multi-task"}}
```

| Position | Parameter | Default | Options |
|---|---|---|---|
| 1 | style | `horizontal` | `horizontal`, `radial`, `hill` |
| 2 | status text | `Done` | any string |
| 3 | show percent | `false` | `true`, `false` |
| 4 | exclude block refs | `false` | `true`, `false` |
| 5 | show hill labels | `true` | `true`, `false` *(hill only)* |
| 6 | multi-task mode | `false` | `true`, `false` *(hill only)* |

#### Basic Usage
```
{{[[roam/render]]:((roam-render-todo-progress-cljs))}}
```

#### Visualization Style
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "radial"}}
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "hill"}}
```

#### Custom Label
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "horizontal" "Complete"}}
```

#### Embedded Blocks
By default the component scans all child blocks **including** pure block references (embeds like `((block-uid))`). To exclude reference-only blocks from the count, set the 4th parameter to `"true"`:
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "horizontal" "Done" "false" "true"}}
```

---

## Hill Diagram

The hill diagram visualizes work as a dot moving over a bell curve. The left half represents the "figuring things out" phase and the right half represents the "making it happen" phase. A summit flag appears when all TODOs are done.

### Flat mode (default)
All TODOs under the block are treated as one task. A single dot moves across the hill as work is completed.

```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "hill"}}
```

### Multi-task mode
Each direct child block is treated as a separate task. Sub-blocks at any depth below each child are counted as that task's TODOs. Each task gets its own colored dot — hover over a dot to see the task name.

```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "hill" "Done" "false" "false" "true" "true"}}
```

Example structure:
```
- {{roam/render: ...}} "hill" ... "true"}}
    - Write the proposal
        - {{TODO}} Draft outline
        - {{TODO}} Add budget section
        - {{DONE}} Executive summary
    - Review feedback
        - {{DONE}} Collect comments
        - {{TODO}} Revise draft
    - Final delivery
        - {{TODO}} Format document
        - {{TODO}} Send to stakeholders
```

Each of the three top-level blocks ("Write the proposal", "Review feedback", "Final delivery") becomes its own dot on the hill, colored and independently positioned based on its own TODO/DONE ratio.

---

## Troubleshooting
If you're having issues with the component:

1. Make sure user code is enabled in your Roam settings
2. Verify the Todo Progress Bar extension is installed from Roam Depot
3. If the component displays an error, try refreshing the page
4. For inline usage issues, check that you haven't accidentally added extra spaces

## Contributing
Feel free to suggest improvements or report bugs by opening an issue on GitHub.

## License
[MIT License](LICENSE)
