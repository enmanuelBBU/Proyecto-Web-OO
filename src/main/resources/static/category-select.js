document.addEventListener('change', function (e) {
  if (e.target.name !== 'categoriaSeleccion') return;
  var field = e.target.closest('.categoria-field');
  if (!field) return;
  var otraInput = field.querySelector('input[name="categoriaOtra"]');
  if (!otraInput) return;
  otraInput.style.display = e.target.value === 'otra' ? 'inline-block' : 'none';
});
