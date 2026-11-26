/*
 * SnackFlow - comportamento compartilhado do layout.
 *  - ícones Lucide
 *  - menu lateral (recolher no desktop, abrir/fechar no celular)
 *  - toasts (vindos do servidor via flash ok/erro, ou chamados com SnackFlow.toast)
 *  - modal de confirmação (qualquer elemento com data-confirmar-mensagem)
 *  - feedback de carregamento nos botões de envio
 * Não duplique esta lógica nos módulos: use os atributos data-* documentados em docs/GUIA-DO-MODULO.md.
 */
(function () {
  'use strict';

  var ICONES = { sucesso: 'check-circle-2', erro: 'x-circle', aviso: 'alert-triangle', info: 'info' };
  var TITULOS = { sucesso: 'Sucesso', erro: 'Erro', aviso: 'Atenção', info: 'Informação' };
  var CHAVE_MENU = 'sf.menu.recolhido';
  var DURACAO_TOAST = 4500;

  function criarIcones() {
    if (window.lucide) {
      window.lucide.createIcons();
    }
  }

  function guardar(chave, valor) {
    try { window.localStorage.setItem(chave, valor); } catch (e) { /* armazenamento indisponível */ }
  }

  function ler(chave) {
    try { return window.localStorage.getItem(chave); } catch (e) { return null; }
  }

  /* ---------------- Toast ---------------- */
  function toast(titulo, mensagem, tipo) {
    var variante = ICONES[tipo] ? tipo : 'sucesso';
    var area = document.getElementById('toast-container');
    if (!area) { return; }

    var el = document.createElement('div');
    el.className = 'sf-toast sf-toast-' + variante;
    el.setAttribute('role', variante === 'erro' ? 'alert' : 'status');

    var icone = document.createElement('i');
    icone.setAttribute('data-lucide', ICONES[variante]);
    var corpo = document.createElement('div');
    corpo.style.flex = '1';
    var t = document.createElement('div');
    t.style.cssText = 'font-size:12px;font-weight:700';
    t.textContent = titulo || TITULOS[variante];
    var m = document.createElement('div');
    m.style.cssText = 'font-size:12px;color:#64748B;margin-top:2px';
    m.textContent = mensagem || '';
    var fechar = document.createElement('button');
    fechar.type = 'button';
    fechar.setAttribute('aria-label', 'Fechar aviso');
    fechar.style.cssText = 'border:0;background:none;cursor:pointer;color:#64748B;font-size:16px;line-height:1';
    fechar.textContent = '×';

    corpo.appendChild(t);
    corpo.appendChild(m);
    el.appendChild(icone);
    el.appendChild(corpo);
    el.appendChild(fechar);
    area.appendChild(el);
    criarIcones();

    function remover() {
      el.style.opacity = '0';
      window.setTimeout(function () { el.remove(); }, 300);
    }
    fechar.addEventListener('click', remover);
    window.setTimeout(remover, DURACAO_TOAST);
  }

  function toastsDoServidor() {
    document.querySelectorAll('#toasts-servidor [data-mensagem]').forEach(function (el) {
      toast(el.getAttribute('data-titulo'), el.getAttribute('data-mensagem'), el.getAttribute('data-tipo'));
    });
  }

  /* ---------------- Modal de confirmação ---------------- */
  var modal, ultimoFoco, aoConfirmar;

  function abrirModal(opcoes) {
    modal = modal || document.getElementById('modal-confirmacao');
    if (!modal) { return; }
    ultimoFoco = document.activeElement;
    aoConfirmar = opcoes.aoConfirmar;
    document.getElementById('modal-confirmacao-titulo').textContent = opcoes.titulo || 'Confirmar ação';
    document.getElementById('modal-confirmacao-mensagem').textContent = opcoes.mensagem || '';
    var botao = document.getElementById('modal-confirmacao-confirmar');
    botao.textContent = opcoes.rotulo || 'Confirmar';
    botao.className = 'sf-btn ' + (opcoes.perigo === false ? 'sf-btn-primario' : 'sf-btn-perigo');
    modal.hidden = false;
    document.getElementById('modal-confirmacao-cancelar').focus();
  }

  function fecharModal() {
    if (!modal) { return; }
    modal.hidden = true;
    aoConfirmar = null;
    if (ultimoFoco && ultimoFoco.focus) { ultimoFoco.focus(); }
  }

  function configurarModal() {
    modal = document.getElementById('modal-confirmacao');
    if (!modal) { return; }
    document.getElementById('modal-confirmacao-cancelar').addEventListener('click', fecharModal);
    modal.addEventListener('click', function (e) { if (e.target === modal) { fecharModal(); } });
    document.getElementById('modal-confirmacao-confirmar').addEventListener('click', function () {
      var acao = aoConfirmar;
      fecharModal();
      if (acao) { acao(); }
    });
    document.addEventListener('keydown', function (e) {
      if (modal.hidden) { return; }
      if (e.key === 'Escape') { fecharModal(); }
      if (e.key === 'Tab') { prenderFoco(e); }
    });
  }

  function prenderFoco(e) {
    var focaveis = modal.querySelectorAll('button:not([disabled])');
    var primeiro = focaveis[0];
    var ultimo = focaveis[focaveis.length - 1];
    if (e.shiftKey && document.activeElement === primeiro) {
      e.preventDefault();
      ultimo.focus();
    } else if (!e.shiftKey && document.activeElement === ultimo) {
      e.preventDefault();
      primeiro.focus();
    }
  }

  /** Confirmação por atributo: <button data-confirmar-mensagem="..." [data-confirmar-titulo] [data-confirmar-rotulo]>. */
  function interceptarConfirmacoes() {
    document.addEventListener('click', function (e) {
      var alvo = e.target.closest('[data-confirmar-mensagem]');
      if (!alvo) { return; }
      if (alvo.dataset.confirmado === 'sim') {
        delete alvo.dataset.confirmado;
        return;
      }
      e.preventDefault();
      abrirModal({
        titulo: alvo.getAttribute('data-confirmar-titulo'),
        mensagem: alvo.getAttribute('data-confirmar-mensagem'),
        rotulo: alvo.getAttribute('data-confirmar-rotulo'),
        perigo: alvo.getAttribute('data-confirmar-tipo') !== 'padrao',
        aoConfirmar: function () {
          alvo.dataset.confirmado = 'sim';
          alvo.click();
        }
      });
    });
  }

  /* ---------------- Carregamento nos envios ---------------- */
  function marcarCarregando(botao) {
    if (botao.classList.contains('sf-carregando')) { return; }
    botao.dataset.conteudoOriginal = botao.innerHTML;
    botao.classList.add('sf-carregando');
    botao.setAttribute('aria-busy', 'true');
    var spinner = document.createElement('span');
    spinner.className = 'sf-spinner';
    spinner.setAttribute('aria-hidden', 'true');
    var texto = document.createElement('span');
    texto.textContent = botao.getAttribute('data-texto-carregando') || 'Processando…';
    botao.replaceChildren(spinner, texto);
    window.setTimeout(function () { botao.disabled = true; }, 0);
  }

  function restaurarBotoes() {
    document.querySelectorAll('.sf-carregando').forEach(function (botao) {
      botao.innerHTML = botao.dataset.conteudoOriginal || botao.innerHTML;
      botao.classList.remove('sf-carregando');
      botao.removeAttribute('aria-busy');
      botao.disabled = false;
    });
    criarIcones();
  }

  function configurarCarregamento() {
    document.addEventListener('submit', function (e) {
      var form = e.target;
      if (form.hasAttribute('data-sem-carregamento') || e.defaultPrevented) { return; }
      var botoes = form.querySelectorAll('button[type="submit"], button:not([type])');
      botoes.forEach(marcarCarregando);
    });
    window.addEventListener('pageshow', function (e) { if (e.persisted) { restaurarBotoes(); } });
  }

  /* ---------------- Menu lateral ---------------- */
  function configurarMenu() {
    var lateral = document.getElementById('sidebar');
    if (!lateral) { return; }
    var overlay = document.getElementById('sidebar-overlay');
    var abrirMovel = document.getElementById('btn-menu-movel');
    var recolher = document.getElementById('btn-recolher-menu');

    if (ler(CHAVE_MENU) === '1') { lateral.classList.add('sf-recolhida'); }

    if (recolher) {
      recolher.addEventListener('click', function () {
        var recolhida = lateral.classList.toggle('sf-recolhida');
        guardar(CHAVE_MENU, recolhida ? '1' : '0');
        recolher.setAttribute('aria-expanded', String(!recolhida));
      });
    }

    function alternarMovel(abrir) {
      lateral.classList.toggle('-translate-x-full', !abrir);
      if (overlay) { overlay.hidden = !abrir; }
      if (abrirMovel) { abrirMovel.setAttribute('aria-expanded', String(abrir)); }
    }
    if (abrirMovel) { abrirMovel.addEventListener('click', function () { alternarMovel(true); }); }
    if (overlay) { overlay.addEventListener('click', function () { alternarMovel(false); }); }
  }

  window.SnackFlow = { toast: toast, confirmar: abrirModal, criarIcones: criarIcones };

  document.addEventListener('DOMContentLoaded', function () {
    criarIcones();
    configurarMenu();
    configurarModal();
    interceptarConfirmacoes();
    configurarCarregamento();
    toastsDoServidor();
  });
})();
