import { toggleRenderComponent } from "./entry-helpers";
import { toggleStrikethroughCSS } from "./entry-helpers";

const codeBlockUID = 'roam-render-todo-progress-cljs';
const cssBlockUID = 'roam-render-todo-progress-css';
const renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`;
const replacementString = '{{todo-progress-bar}}';
const version = 'v11';
const titleblockUID = 'roam-render-todo-progress';
const cssBlockParentUID = 'todo-progress-css-parent';

function onload({extensionAPI}) {
  const panelConfig = {
    tabTitle: "TODO Progress Bar",
    settings: [
        {id:		  "strikethrough",
          name:		"Strikethrough DONE tasks",
          description: "Adds CSS to strike through DONE tasks",
          action:	  {type:	 "switch",
                        onChange: (evt) => { 
                          toggleStrikethroughCSS(evt.target.checked); 
                          console.log("toggle strikethrough CSS!", evt.target.checked); }}}
    ]
  };

  extensionAPI.settings.panel.create(panelConfig);

  if (!roamAlphaAPI.data.pull("[*]", [":block/uid", titleblockUID])) {
    // component hasn't been loaded so we add it to the graph
    toggleRenderComponent(true, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID)
  }

  console.log("load todo progress bar plugin");
}

function onunload() {
  console.log("unload todo progress bar plugin");
  toggleRenderComponent(false, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID)
}

export default {
onload,
onunload
};
