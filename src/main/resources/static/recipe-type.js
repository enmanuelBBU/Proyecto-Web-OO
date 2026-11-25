// Alterna el editor de receta entre la grilla 3x3 (mesa de crafteo) y la casilla única del horno.
(function () {
  function aplicar(select) {
    var form = select.closest('form');
    if (!form) return;
    var horno = select.value === 'HORNO';
    form.querySelectorAll('.recipe-grid, .recipe-grid-hint').forEach(function (el) {
      el.style.display = horno ? 'none' : '';
    });
    form.querySelectorAll('.furnace-slot, .furnace-hint').forEach(function (el) {
      el.hidden = !horno;
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.recipe-type-select').forEach(aplicar);
  });

  document.addEventListener('change', function (e) {
    if (e.target.classList && e.target.classList.contains('recipe-type-select')) aplicar(e.target);
  });
})();
