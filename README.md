# Roam Research Progress Bar Component

A highly customizable visual progress bar component for tracking TODOs in your Roam Research graph.

## Features
- Track TODO/DONE items within a block and its children
- Choose between horizontal or radial (circle) visualization styles
- Customizable status text label
- Option to include embedded blocks in counting
- Inline compatibility - works within text without breaking lines
- Configurable through an easy-to-use settings menu
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
The component now includes a built-in settings menu accessible by hovering over the progress bar and clicking the gear icon that appears:

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/settings-menu-example.png" width="300"></img>

In the settings menu, you can:
- Change between horizontal and radial display styles
- Customize the status text
- Toggle whether to include embedded blocks in the count

### Manual Customization
You can also manually customize the component by passing parameters:

#### Basic Usage
```
{{[[roam/render]]:((roam-render-todo-progress-cljs))}}
```

#### Visualization Style
Choose between "horizontal" (default) or "radial" display:
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "radial"}}
```

#### Custom Label
Change the status text (default is "Done"):
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "horizontal" "Complete"}}
```

#### Include Embedded Blocks
Count TODOs in embedded blocks as well:
```
{{[[roam/render]]:((roam-render-todo-progress-cljs)) "horizontal" "Done" "include-embeds"}}
```

## How It Works
The progress bar scans through the block where it's placed and all child blocks, counting TODO and DONE markers. It then calculates completion percentage and visualizes it accordingly.

When the "include-embeds" option is enabled, it will also scan through any embedded blocks to count their TODOs as well.

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