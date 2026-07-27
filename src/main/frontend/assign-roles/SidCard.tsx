import { Card } from "../common/components/Card.tsx";
import { IconButton } from "../common/components/IconButton.tsx";
import { EditIcon } from "../common/components/icons/EditIcon.tsx";
import { PeopleIcon } from "../common/components/icons/PeopleIcon.tsx";
import { PersonIcon } from "../common/components/icons/PersonIcon.tsx";
import { TrashIcon } from "../common/components/icons/TrashIcon.tsx";
import { WarningIcon } from "../common/components/icons/WarningIcon.tsx";
import { Tooltip } from "../common/components/Tooltip.tsx";
import type { SidEntry, SidInfo } from "../common/types/assignment.ts";
import type { Role } from "../common/types/role.ts";

interface SidCardProps {
  entry: SidEntry;
  /** Realm lookup result; undefined while the batch lookup is in flight. */
  info?: SidInfo;
  /** All roles of the active role type, in display order. */
  roles: Role[];
  canEdit: boolean;
  /** The reserved anonymous/authenticated entries cannot be removed. */
  internal: boolean;
  onEdit: (entry: SidEntry) => void;
  onDelete: (entry: SidEntry) => void;
  onMigrate: (entry: SidEntry, to: "USER" | "GROUP") => void;
}

const kindLabel = (entry: SidEntry) =>
  entry.type === "GROUP" ? "group" : "user";

export function SidCard({
  entry,
  info,
  roles,
  canEdit,
  internal,
  onEdit,
  onDelete,
  onMigrate,
}: SidCardProps) {
  const notFound = info?.resolution === "not-found";
  const displayName = info?.displayName;

  const name = (
    <Tooltip
      content={
        notFound
          ? `${entry.type === "GROUP" ? "Group" : "User"} not found in the security realm`
          : undefined
      }
      placement="top"
    >
      <span
        className={notFound ? "rsp-assign__name--not-found" : undefined}
        data-sid={entry.name}
      >
        {displayName ? `${displayName} (${entry.name})` : entry.name}
      </span>
    </Tooltip>
  );

  const badges = (
    <>
      {entry.type === "EITHER" && (
        <Tooltip
          content="Applies to both a user and a group of this name. Migrate it to an explicit user or group entry."
          placement="top"
        >
          <span className="rsp-assign__badge rsp-assign__badge--warning">
            <WarningIcon />
            Ambiguous
          </span>
        </Tooltip>
      )}
      {internal && (
        <Tooltip
          content={
            entry.name === "anonymous"
              ? "Built-in: everyone who is not logged in"
              : "Built-in: everyone who is logged in"
          }
          placement="top"
        >
          <span className="rsp-card__template-badge">Built-in</span>
        </Tooltip>
      )}
    </>
  );

  const actions = canEdit && (
    <>
      {entry.type === "EITHER" && (
        <>
          <IconButton
            tooltip="Migrate to user"
            onClick={() => onMigrate(entry, "USER")}
            icon={<PersonIcon />}
          />
          <IconButton
            tooltip="Migrate to group"
            onClick={() => onMigrate(entry, "GROUP")}
            icon={<PeopleIcon />}
          />
        </>
      )}
      <IconButton
        tooltip="Edit roles"
        onClick={() => onEdit(entry)}
        icon={<EditIcon />}
      />
      {!internal && (
        <IconButton
          tooltip={`Remove ${kindLabel(entry)}`}
          destructive
          onClick={() => onDelete(entry)}
          icon={<TrashIcon />}
        />
      )}
      {internal && <div className="rsp-card__action"></div>}
    </>
  );

  // Assigned roles in catalogue order; names of since-deleted roles (not in
  // the catalogue anymore) are appended so nothing is silently hidden.
  const assigned = [
    ...roles.filter((r) => entry.roles.includes(r.name)),
    ...entry.roles
      .filter((name) => !roles.some((r) => r.name === name))
      .map((name) => ({ name }) as Role),
  ];

  return (
    <Card
      icon={entry.type === "GROUP" ? <PeopleIcon /> : <PersonIcon />}
      name={name}
      badges={badges}
      summary={entry.roles.length > 0 ? entry.roles.join(", ") : undefined}
      summaryPlaceholder="No roles assigned"
      actions={actions}
      readOnly={!canEdit}
      body={
        // Read-only view of the assigned roles, for when there are more than
        // fit in the collapsed summary; editing happens in the dialog.
        <div className="rsp-assign__chips">
          {assigned.map((role) => (
            <span
              key={role.name}
              className="rsp-assign__chip"
              data-role-name={role.name}
            >
              {role.name}
              {role.pattern !== undefined && (
                <span className="rsp-card__pattern">{role.pattern}</span>
              )}
            </span>
          ))}
        </div>
      }
    />
  );
}
