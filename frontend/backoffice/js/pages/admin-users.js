import { api, iam } from '../api.js';
import { state, isAdmin } from '../state.js';
import { h, badge, dialog, emptyState, errorBox, field, loading, pageHead, panel, table, toast, fill } from '../ui.js';

const GROWER_SCOPES = 'greenhouse:read greenhouse:control incidents:manage';
const ADMIN_SCOPES = `${GROWER_SCOPES} admin`;

function roleChecks(selected = ['GROWER']) {
  return h('div', { class: 'checks' }, ['GROWER', 'ADMIN'].map((role) =>
    h('label', {}, h('input', { type: 'checkbox', value: role, checked: selected.includes(role) }), ` ${role === 'ADMIN' ? 'Administrator' : 'Grower'}`)));
}

function siteChecks(selected = []) {
  return h('div', { class: 'checks' }, state.sites.map((s) =>
    h('label', {}, h('input', { type: 'checkbox', value: s.id, checked: selected.includes(s.id) }), ` ${s.name}`)));
}

const checked = (box) => [...box.querySelectorAll('input:checked')].map((c) => c.value);

async function editUser(identity, profile, done) {
  let etag = null;
  if (identity) etag = (await iam.getTagged(`/identities/${identity.id}`)).etag;

  const email = h('input', { type: 'email', required: true, value: identity?.username || '', disabled: Boolean(identity) });
  const name = h('input', { value: identity?.displayName || '', maxlength: 100 });
  const password = h('input', { type: 'password', minlength: 8, maxlength: 128, required: !identity, autocomplete: 'new-password' });
  const roles = roleChecks(identity?.roleNames || ['GROWER']);
  const sites = siteChecks(profile?.siteIds || []);
  const active = h('input', { type: 'checkbox', checked: identity ? identity.active !== false : true });
  const form = h('form', { class: 'stack', id: 'user-form' },
    field('E-mail', email), field('Display name', name),
    field(identity ? 'New password' : 'Password', password, identity ? 'Leave empty to keep the current one' : 'At least 8 characters'),
    field('Roles', roles), field('Greenhouses this person can see', sites, 'Administrators see every greenhouse.'),
    identity ? h('label', { class: 'check-line' }, active, ' Account active') : null);
  const save = h('button', { class: 'btn btn-primary', type: 'submit', form: 'user-form' }, 'Save');
  const { close } = dialog(identity ? `Edit ${identity.username}` : 'New account', form, [save]);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const roleList = checked(roles);
    if (!roleList.length) {
      toast('Choose at least one role.', 'error');
      return;
    }
    const body = {
      displayName: name.value.trim() || null,
      roles: roleList,
      scopes: roleList.includes('ADMIN') ? ADMIN_SCOPES : GROWER_SCOPES,
    };
    if (password.value) body.password = password.value;
    save.disabled = true;
    try {
      let id = identity?.id;
      if (identity) {
        await iam.put(`/identities/${id}`, { ...body, active: active.checked }, etag);
      } else {
        id = (await iam.post('/identities', { username: email.value.trim(), ...body })).id;
      }
      await api.put(`/users/${id}/sites`, { siteIds: checked(sites) });
      toast('Account saved.');
      close();
      done();
    } catch (error) {
      toast(error.message, 'error');
      save.disabled = false;
    }
  });
}

export async function render(view) {
  if (!isAdmin()) {
    fill(view, emptyState('Administrators only'));
    return;
  }
  const list = panel(null, loading());
  fill(view, 
    pageHead('Users & access', 'Accounts, roles and the greenhouses each person can see',
      h('button', { class: 'btn btn-primary', onclick: () => editUser(null, null, load) }, 'New account')),
    list);

  async function load() {
    try {
      const [identities, profiles] = await Promise.all([iam.get('/identities'), api.get('/users')]);
      const profileOf = (id) => profiles.find((p) => p.id === id);
      const siteName = (id) => state.sites.find((s) => s.id === id)?.name || id;
      fill(list, identities.length
        ? table([
            { label: 'Name', cell: (u) => h('div', {}, h('strong', {}, u.displayName || u.username), h('small', { class: 'muted block' }, u.username)) },
            { label: 'Roles', cell: (u) => (u.roleNames || []).map((r) => badge(r)) },
            { label: 'Greenhouses', cell: (u) => ((u.roleNames || []).includes('ADMIN') ? 'All' : (profileOf(u.id)?.siteIds || []).map(siteName).join(', ') || 'None') },
            { label: 'Status', cell: (u) => badge(u.active === false ? 'DISABLED' : 'ACTIVE') },
            { label: '', cell: (u) => h('button', { class: 'btn btn-small', onclick: (e) => { e.stopPropagation(); editUser(u, profileOf(u.id), load).catch((x) => toast(x.message, 'error')); } }, 'Edit') },
          ], identities)
        : emptyState('No account'));
    } catch (error) {
      fill(list, errorBox(error));
    }
  }

  await load();
}
