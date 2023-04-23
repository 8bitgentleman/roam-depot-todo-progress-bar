import toggleRenderComponent from "./entry-helpers";

const codeBlockUID = 'roam-render-todo-progress-cljs';
const cssBlockUID = 'roam-render-todo-progress-css';
const renderString = `{{[[roam/render]]:((${codeBlockUID}))}}`;
const replacementString = '{{todo-progress-bar}}';
const version = 'v11';
const titleblockUID = 'roam-render-todo-progress';
const cssBlockParentUID = 'todo-progress-css-parent';

function onload() {
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
