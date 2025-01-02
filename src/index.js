import { toggleRenderComponent } from "./entry-helpers";
import { toggleStrikethroughCSS } from "./entry-helpers";
//setting this up to be as generic as possible
//should mostly need to edit the top variables when creating a new extension/version
const componentName = 'TODO Progress Bar';
const version = 'v12';

const componentLowerName = componentName.replaceAll(" ", "-").toLowerCase();
const codeBlockUID = 'roam-render-todo-progress-cljs';
const cssBlockUID = 'roam-render-todo-progress-css';
const renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`;
const replacementString = '{{todo-progress-bar}}';
const titleblockUID = 'roam-render-todo-progress';
const cssBlockParentUID = 'todo-progress-css-parent';

async function onload({extensionAPI}) {
  const panelConfig = {
      tabTitle: componentName,
      settings: [{
          id: "strikethrough",
          name: "Strikethrough DONE tasks",
          description: "Adds CSS to strike through DONE tasks",
          action: {
              type: "switch",
              onChange: async (evt) => {
                await toggleStrikethroughCSS(evt.target.checked);
                console.log("toggle strikethrough CSS!", evt.target.checked);
            }
          }
      }]
  };

  extensionAPI.settings.panel.create(panelConfig);

  try {
      if (!roamAlphaAPI.data.pull("[*]", [":block/uid", titleblockUID])) {
          await toggleRenderComponent(true, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName);
      }
      console.log(`loaded ${componentName} plugin`);
  } catch (error) {
      console.error('Error loading plugin:', error);
  }
}

function onunload() {
  console.log(`unload ${componentName} plugin`)
  toggleRenderComponent(false, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName)
}

export default {
onload,
onunload
};
