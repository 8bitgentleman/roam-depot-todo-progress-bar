import { toggleRenderComponent, toggleStrikethroughCSS, updateCodeBlock, updateCSSBlock } from "./entry-helpers";
//setting this up to be as generic as possible
//should mostly need to edit the top variables when creating a new extension/version
const componentName = 'TODO Progress Bar';
const version = 'v14';

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

  window.todoProgressBarExtensionData = {running: true};
  console.log(`Loading ${componentName} plugin version ${version}`);
  
  try {
      // Check if the component block exists
      const existingComponent = roamAlphaAPI.data.pull("[*]", [":block/uid", titleblockUID]);
      console.log(`Component block check (${titleblockUID}):`, existingComponent ? "Found" : "Not found");
      
      // Check if the code block exists 
      const existingCodeBlock = roamAlphaAPI.data.pull("[*]", [":block/uid", codeBlockUID]);
      console.log(`Code block check (${codeBlockUID}):`, existingCodeBlock ? "Found" : "Not found");
      
      if (!existingComponent) {
          console.log("Component blocks not found, creating new blocks...");
          await toggleRenderComponent(true, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName);
      } else {
          console.log("Component blocks exist, checking if code block needs updating...");
          
          // Now explicitly update the code block content even if it exists
          if (existingCodeBlock) {
              console.log("Updating existing code block with latest version...");
              await updateCodeBlock(codeBlockUID);
          } else {
              console.log("Code block not found despite component existing - may need repair");
              // TODO could implement repair logic here if needed
          }
          
          // Also update the CSS block
          const existingCSSBlock = roamAlphaAPI.data.pull("[*]", [":block/uid", cssBlockUID]);
          if (existingCSSBlock) {
              console.log("Updating CSS block...");
              await updateCSSBlock(cssBlockUID);
          }
      }
      console.log(`Completed loading ${componentName} plugin`);
  } catch (error) {
      console.error('Error loading plugin:', error);
  }
}

function onunload() {
  window.todoProgressBarExtensionData = null;
  toggleRenderComponent(false, titleblockUID, cssBlockParentUID, version, renderString, replacementString, cssBlockUID, codeBlockUID, componentName)
  console.log(`unload ${componentName} plugin`)
}

export default {
onload,
onunload
};
