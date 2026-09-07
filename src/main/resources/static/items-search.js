(function () {
  var q = document.getElementById('items-buscar-q');
  var esMateriaPrima = document.getElementById('items-buscar-materia-prima');
  var tabla = document.getElementById('items-resultados-tabla');
  var tbody = document.getElementById('items-resultados');
  if (!q || !tbody || !tabla) return;

  var timer = null;

  function buscar() {
    var texto = q.value.trim();
    if (!texto && !esMateriaPrima.checked) {
      tabla.style.display = 'none';
      return;
    }
    tabla.style.display = '';

    var params = new URLSearchParams();
    if (texto) params.set('q', texto);
    if (esMateriaPrima.checked) params.set('esMateriaPrima', 'true');

    fetch('/api/items?' + params.toString())
      .then(function (res) { return res.json(); })
      .then(renderFilas)
      .catch(function () {
        tbody.innerHTML = '<tr><td colspan="5">No se pudo buscar items.</td></tr>';
      });
  }

  function renderFilas(itemsList) {
    tbody.innerHTML = '';
    if (!itemsList.length) {
      tbody.innerHTML = '<tr><td colspan="5">No se encontraron items.</td></tr>';
      return;
    }
    itemsList.forEach(function (item) {
      var tr = document.createElement('tr');

      var tdIcono = document.createElement('td');
      if (item.fullId) {
        var img = document.createElement('img');
        img.src = 'https://blocksitems.com/api/v1/items/' + item.fullId + '/icon?size=32';
        img.width = 32; img.height = 32; img.alt = '';
        img.onerror = function () { this.style.display = 'none'; };
        tdIcono.appendChild(img);
      }
      tr.appendChild(tdIcono);

      var tdNombre = document.createElement('td');
      tdNombre.textContent = item.nombre;
      tr.appendChild(tdNombre);

      var tdCategoria = document.createElement('td');
      var badge = document.createElement('span');
      badge.className = 'badge badge-pendiente';
      badge.textContent = item.categoria || '';
      tdCategoria.appendChild(badge);
      tr.appendChild(tdCategoria);

      var tdMateriaPrima = document.createElement('td');
      tdMateriaPrima.className = 'mono';
      tdMateriaPrima.textContent = item.esMateriaPrima ? 'Sí' : 'No';
      tr.appendChild(tdMateriaPrima);

      var tdAcciones = document.createElement('td');
      var editar = document.createElement('a');
      editar.href = '/items/' + encodeURIComponent(item.id) + '/edit';
      editar.textContent = 'Editar';
      tdAcciones.appendChild(editar);
      tdAcciones.appendChild(document.createTextNode(' '));

      var formEliminar = document.createElement('form');
      formEliminar.method = 'post';
      formEliminar.action = '/items/' + encodeURIComponent(item.id) + '/delete';
      formEliminar.style.display = 'inline';
      var btnEliminar = document.createElement('button');
      btnEliminar.className = 'btn-danger btn';
      btnEliminar.textContent = 'Eliminar';
      formEliminar.appendChild(btnEliminar);
      tdAcciones.appendChild(formEliminar);

      tr.appendChild(tdAcciones);
      tbody.appendChild(tr);
    });
  }

  q.addEventListener('input', function () {
    clearTimeout(timer);
    timer = setTimeout(buscar, 300);
  });

  esMateriaPrima.addEventListener('change', buscar);
})();
