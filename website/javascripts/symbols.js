// Symbols page: the filter box hides cells without every typed word; clicking a cell copies its :name:.
document.addEventListener("DOMContentLoaded", () => {
  const filter = document.querySelector(".sym-filter");
  if (!filter) return;
  const cells = [...document.querySelectorAll(".sym")];
  filter.addEventListener("input", () => {
    const words = filter.value.toLowerCase().split(/\s+/).filter(Boolean);
    cells.forEach(cell => cell.classList.toggle("hidden", !words.every(w => cell.dataset.search.includes(w))));
    document.querySelectorAll(".sym-section").forEach(section =>
      section.classList.toggle("hidden", !section.querySelector(".sym:not(.hidden)")));
  });
  document.addEventListener("click", e => {
    const cell = e.target.closest(".sym");
    if (cell) navigator.clipboard.writeText(cell.dataset.copy).then(() => { cell.title = "Copied " + cell.dataset.copy; });
  });
});
