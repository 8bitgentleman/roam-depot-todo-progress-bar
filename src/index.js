import { toggleRenderComponent } from "./entry-helpers";
import { toggleStrikethroughCSS } from "./entry-helpers";
//setting this up to be as generic as possible
//should mostly need to edit the top variables when creating a new extension/version
const componentName = 'TODO Progress Bar';
const version = 'v11';

const componentLowerName = componentName.replace(" ", "-").toLowerCase();
const codeBlockUID = `roam-render-${componentLowerName}-cljs`;
const cssBlockUID = `roam-render-${componentLowerName}-css`;
const renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`;
const replacementString = `{{${componentLowerName}}}`;
const titleblockUID = `roam-render-${componentLowerName}`;
const cssBlockParentUID = `${componentLowerName}-css-parent`;

function onload({extensionAPI}) {
  const panelConfig = {
    tabTitle: componentName,
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
    toggleRenderComponent(true, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName)
  }

  console.log(`load ${componentName} plugin`)
}

function onunload() {
  console.log(`unload ${componentName} plugin`)
  toggleRenderComponent(false, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName)
}

export default {
onload,
onunload
};
