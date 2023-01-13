import toggleProgressBar from "./todo_progress_bar";

function findBlockByUID(uid){
  return roamAlphaAPI.q(
      `[:find (pull ?e [:block/uid]) :where [?e :block/uid "${uid}"]]`
      )?.[0]?.[0].uid || null
}

function onload({extensionAPI}) {
  let titleblockUID = 'todo-progress';
  if (!roamAlphaAPI.data.pull("[*]", [":block/uid", titleblockUID])) {
    // component hasn't been loaded so we add it to the graph
    toggleProgressBar(true)
  }

  console.log("load example plugin");
}

function onunload() {
  console.log("unload example plugin");
  toggleProgressBar(false)
}

export default {
onload,
onunload
};
