Roam Research progress bar component for visually tracking TODOs in a list.

## Example 
<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/example.gif" max-width="400"></img>

## Setup 
First make sure that __User code__ is enabled in your settings. This allows custom components in your graph.

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/settings.png" width="300"></img>

## Usage
Easiest way to insert the component is though Roam's native template menu. Simply type `;;` and look for __TODO Progress Bar__

<img src="https://github.com/8bitgentleman/roam-depot-todo-progress-bar/raw/main/template.png" max-width="400"></img>

## Customization
The TODO progress bar now comes with a circle option. To use it just pass "radial" into the component like this `{{[[roam/render]]:((roam-render-todo-progress-cljs)))  "radial"}}`
