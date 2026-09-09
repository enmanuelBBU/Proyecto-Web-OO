(function () {
  'use strict';

  var TIPOS = {
    success: { icono: '✓', clase: 'toast-success' },
    error: { icono: '✕', clase: 'toast-error' },
    info: { icono: 'ℹ', clase: 'toast-info' },
    warning: { icono: '⚠', clase: 'toast-warning' }
  };
  var DURACION = 4000;

  function contenedor() {
    var caja = document.getElementById('toast-container');
    if (!caja) {
      caja = document.createElement('div');
      caja.id = 'toast-container';
      caja.className = 'toast-container';
      caja.setAttribute('aria-live', 'polite');
      caja.setAttribute('aria-atomic', 'false');
      document.body.appendChild(caja);
    }
    return caja;
  }

  function eliminar(el) {
    el.classList.add('toast-fade-out');
    setTimeout(function () {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 300);
  }

  window.mostrarToast = function (mensaje, tipo) {
    if (!mensaje) return;
    tipo = TIPOS[tipo] ? tipo : 'success';
    var def = TIPOS[tipo];

    var toast = document.createElement('div');
    toast.className = 'toast ' + def.clase;
    toast.setAttribute('role', 'status');

    var icono = document.createElement('span');
    icono.className = 'toast-icon';
    icono.setAttribute('aria-hidden', 'true');
    icono.textContent = def.icono;

    var texto = document.createElement('span');
    texto.className = 'toast-mensaje';
    texto.textContent = mensaje;

    var cerrar = document.createElement('button');
    cerrar.type = 'button';
    cerrar.className = 'toast-close';
    cerrar.setAttribute('aria-label', 'Cerrar notificación');
    cerrar.textContent = '×';
    cerrar.addEventListener('click', function () {
      eliminar(toast);
    });

    toast.appendChild(icono);
    toast.appendChild(texto);
    toast.appendChild(cerrar);
    contenedor().appendChild(toast);

    setTimeout(function () {
      eliminar(toast);
    }, DURACION);
  };
})();